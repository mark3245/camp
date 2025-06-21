package ru.campus.parsers.togudv

import io.ktor.client.*
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import ru.campus.parser.sdk.DateProvider
import ru.campus.parser.sdk.api.ParserApi
import ru.campus.parser.sdk.api.createDefaultHttpClient
import ru.campus.parser.sdk.base.BaseParser
import ru.campus.parser.sdk.base.EntitiesCollector
import ru.campus.parser.sdk.base.ScheduleCollector
import ru.campus.parser.sdk.model.*
import ru.campus.parser.sdk.utils.*

class TogudvParser @JvmOverloads constructor(
    credentials: Credentials,
    parserApiBaseUrl: String = getParserApiUrl(),
    override val logger: Logger = LogManager.getLogger(TogudvParser::class.java),
    httpClient: HttpClient = createDefaultHttpClient(logger),
    parserApi: ParserApi = ParserApi(
        httpClient = httpClient,
        userName = credentials.username,
        password = credentials.password,
        baseUrl = parserApiBaseUrl
    ),
    private val dateProvider: DateProvider = createDefaultDateProvider(),
) : BaseParser(parserApi) {

    override val isWithoutSchedule: Boolean = false

    private val groupsCollector: EntitiesCollector = TogudvGroupEntitiesCollector(httpClient, logger)
    private val groupsScheduleCollector: ScheduleCollector = TogudvGroupScheduleCollector(httpClient, logger, dateProvider)

    override suspend fun parseInternal(): ParserResult = coroutineScope {
        val groups = async { groupsCollector.collectEntities() }.await()

        val currentDate: LocalDate = dateProvider.getCurrentDateTime().date

        val successfulGroups = async {
            parallelProcessing(groups, "Groups processing") { group ->
                processEntity(
                    groupsScheduleCollector,
                    group,
                    currentDate,
                    emptyList()
                )
            }
        }.await()

        val savedSchedules: Sequence<SavedSchedule> = successfulGroups.map { it.savedSchedule }.asSequence()

        ParserResult(
            entitiesCount = groups.size,
            entitiesWithLesson = successfulGroups.count { it.savedSchedule.schedule.isNotEmpty() },
            errorsCount = groups.size - successfulGroups.size,
            scheduleAddedCount = savedSchedules.sumOf { it.addedCount },
            scheduleUpdatedCount = savedSchedules.sumOf { it.updatedCount },
            savedEntities = savedSchedules.map { it.savedEntity }.toList(),
            savedSchedules = savedSchedules.toList()
        )
    }

    private suspend fun processEntity(
        scheduleCollector: ScheduleCollector,
        entity: Entity,
        currentDate: LocalDate,
        intervals: List<TimeTableInterval>,
    ): ProcessedEntity {
        val result = scheduleCollector.collectSchedule(entity, intervals)

        val savedEntity = saveEntity(result.processedEntity)
        val schedules = generateSchedules(
            currentDate = currentDate,
            weekName = { it.weekNumber.weekName },
            weekScheduleItems = result.weekScheduleItems,
            postprocessIntervals = { it.groupLessonsInIntervals() }
        )
        val savedSchedule = saveSchedule(savedEntity, schedules)

        return ProcessedEntity(savedEntity, savedSchedule)
    }
}
