package ru.campus.parsers.togudv

import io.ktor.client.*
import org.apache.logging.log4j.Logger
import org.jsoup.Jsoup
import ru.campus.parser.sdk.base.EntitiesCollector
import ru.campus.parser.sdk.model.Entity
import java.util.*

class TogudvGroupEntitiesCollector(
    private val httpClient: HttpClient,
    private val logger: Logger
) : EntitiesCollector {

    companion object {
        private const val BASE_URL = "https://togudv.ru/rasp/groups/"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0 Safari/537.36"
    }

    override suspend fun collectEntities(): List<Entity> {
        logger.info("Загружаем список групп с $BASE_URL")
        val result = mutableListOf<Entity>()

        try {
            val doc = Jsoup.connect(BASE_URL)
                .userAgent(USER_AGENT)
                .timeout(15000)
                .get()

            val links = doc.select("li > a")
            for (link in links) {
                val name = link.text().trim()
                val url = link.absUrl("href")
                if (url.isNotBlank()) {
                    val id = Base64.getEncoder().encodeToString(url.toByteArray())
                    result.add(Entity(id = id, name = name, type = "group"))
                }
            }

        } catch (e: Exception) {
            logger.error("Ошибка при получении списка групп: ${e.message}")
        }

        return result
    }
}
