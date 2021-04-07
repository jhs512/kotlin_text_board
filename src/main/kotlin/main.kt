import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

var articleLastId = 0
val articles: MutableList<Article> = mutableListOf()

fun addArticle(title: String, body: String) {
    val id = ++articleLastId
    val newArticle = Article(id, Util.getNowDateStr(), Util.getNowDateStr(), title, body)
    articles.add(newArticle)
}

fun makeTestData() {
    for (i in 1..10) {
        addArticle("제목 ${i}", "내용 ${i}")
    }
}

fun main(args: Array<String>) {
    println("== 텍스트 게시판 시작 ==")

    makeTestData()

    while (true) {
        print("명령어) ")
        val command = readLine()?.trim()

        if (command.isNullOrEmpty())
            continue

        if (command == "article add") {
            print("제목 : ")
            val title = readLine()!!.trim()
            print("내용 : ")
            val body = readLine()!!.trim()

            addArticle(title, body)
        } else if (command == "article list") {
            println("번호 / ${"제목".padEnd(17, ' ')} / ${"수정".padEnd(17, ' ')} / 제목")
            for (article in articles) {
                println("${article.id.toString().padEnd(4, ' ')} / ${article.regDate} / ${article.updateDate} / ${article.title}")
            }
        }
    }

    println("== 텍스트 게시판 끝 ==")
}

data class Article(val id: Int, val regDate: String, val updateDate: String, val title: String, val body: String) {
}

object Util {
    fun getNowDateStr(): String {
        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return current.format(formatter)
    }
}