package ru.campus.parsers.togudv

import io.ktor.client.*
import org.apache.logging.log4j.Logger
import org.jsoup.Jsoup
import ru.campus.parser.sdk.DateProvider
import ru.campus.parser.sdk.base.ScheduleCollector
import ru.campus.parser.sdk.model.Entity
import ru.campus.parser.sdk.model.Lesson
import ru.campus.parser.sdk.model.TimeTableInterval
import ru.campus.parser.sdk.model.WeekScheduleItem
import java.util.*

class TogudvGroupScheduleCollector(
    private val httpClient: HttpClient,
    private val logger: Logger,
    private val dateProvider: DateProvider,
) : ScheduleCollector {

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0 Safari/537.36"
    }

    override suspend fun collectSchedule(
        entity: Entity,
        intervals: List<TimeTableInterval>
    ): ScheduleCollector.Result {
        logger.info("Парсим расписание для ${entity.name}")
        val lessons = mutableListOf<Lesson>()

        try {
            val url = String(Base64.getDecoder().decode(entity.id))
            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(15000)
                .get()

            val table = doc.selectFirst("table") ?: return emptyResult(entity)
            val rows = table.select("tr").drop(1) // skip header

            for (row in rows) {
                val cols = row.select("td")
                if (cols.size >= 6) {
                    val lesson = Lesson(
                        day = cols[0].text().trim(),
                        time = cols[1].text().trim(),
                        subject = cols[2].text().trim(),
                        lessonType = cols[3].text().trim(),
                        room = cols[4].text().trim(),
                        teacher = cols[5].text().trim()
                    )
                    lessons.add(lesson)
                }
            }

        } catch (e: Exception) {
            logger.error("Ошибка при загрузке расписания: ${e.message}")
        }

        val weekItem = WeekScheduleItem(
            weekNumber = 1, // можно улучшить позже
            lessons = lessons
        )

        return ScheduleCollector.Result(
            processedEntity = entity,
            weekScheduleItems = listOf(weekItem)
        )
    }
}
