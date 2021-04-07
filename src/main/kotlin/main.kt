import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun main(args: Array<String>) {
    App.run()
}

data class Article(val id: Int, val regDate: String, val updateDate: String, val title: String, val body: String) {
    constructor(article: Article, title: String, body: String) : this(
        article.id,
        article.regDate,
        Util.getNowDateStr(),
        title,
        body
    )
}

class ArticleDao {
    private var articleLastId = 0
    private val articles: MutableList<Article> by lazy { mutableListOf() }

    @JvmName("getArticles1")
    fun getArticles(): List<Article> {
        return articles
    }

    fun addArticle(title: String, body: String): Int {
        val id = ++articleLastId
        val newArticle = Article(id, Util.getNowDateStr(), Util.getNowDateStr(), title, body)
        articles.add(newArticle)

        return id
    }

    fun getArticleById(id: Int): Article? {
        val index = getArticleIndexById(id)

        if (index == -1) {
            return null
        }

        return articles[index]
    }

    private fun getArticleIndexById(id: Int): Int {
        return articles.indexOfFirst { it.id == id }
    }

    fun deleteById(id: Int) {
        articles.removeAt(getArticleIndexById(id))
    }

    fun modify(id: Int, title: String, body: String) {
        val newArticle = Article(getArticleById(id)!!, title, body)
        articles[getArticleIndexById(id)] = newArticle
    }
}

class ArticleService {
    private val articleDao = Container.articleDao


    fun addArticle(title: String, body: String) {
        articleDao.addArticle(title, body)
    }

    fun getArticles() = articleDao.getArticles()

    fun makeTestData() {
        for (i in 1..10) {
            addArticle("제목 $i", "내용 $i")
        }
    }

    fun getArticleById(id: Int) = articleDao.getArticleById(id)

    fun deleteById(id: Int) = articleDao.deleteById(id)

    fun modify(id: Int, title: String, body: String) = articleDao.modify(id, title, body)
}

object Container {
    val articleDao by lazy {
        ArticleDao()
    }

    val articleService by lazy {
        ArticleService()
    }

    val usrArticleController by lazy {
        UsrArticleController()
    }
}

class UsrArticleController {
    private val articleService = Container.articleService

    init {
        articleService.makeTestData()
    }

    fun callAction(request: Request) {
        when {
            request.path.endsWith("article/list") -> {
                showList(request)
            }
            request.path.endsWith("article/add") -> {
                showAdd(request)
            }
            request.path.endsWith("article/doDelete") -> {
                doDelete(request)
            }
            request.path.endsWith("article/modify") -> {
                showModify(request)
            }
        }
    }

    private fun showModify(request: Request) {
        if (request.getParameter("id").isNullOrBlank()) {
            println("id를 입력해주세요.")
            return
        }

        val id = request.getParameter("id")!!.toInt()

        val article = articleService.getArticleById(id)

        if (article == null) {
            println("${id}번 글은 존재하지 않습니다.")
            return
        }

        println("기존 제목 : ${article.title}")
        print("새 제목 : ")
        val title = readLine()!!.trim()
        println("기존 내용 : ${article.body}")
        print("새 내용 : ")
        val body = readLine()!!.trim()

        doModify(Request("usr/article/doModify?id=$id&title=$title&body=$body"))
    }

    private fun doModify(request: Request) {
        if (request.getParameter("id").isNullOrBlank()) {
            println("id를 입력해주세요.")
            return
        }

        val id = request.getParameter("id")!!.toInt()

        val article = articleService.getArticleById(id)

        if (article == null) {
            println("${id}번 글은 존재하지 않습니다.")
            return
        }

        val title = request.getParameter("title")!!
        val body = request.getParameter("body")!!

        articleService.modify(id, title, body)

        println("${id}번 글을 수정하였습니다.")
    }

    private fun doDelete(request: Request) {
        if (request.getParameter("id").isNullOrBlank()) {
            println("id를 입력해주세요.")
            return
        }

        val id = request.getParameter("id")!!.toInt()

        val article = articleService.getArticleById(id)

        if (article == null) {
            println("${id}번 글은 존재하지 않습니다.")
            return
        }

        articleService.deleteById(id)

        println("${id}번 글을 삭제하였습니다.")
    }

    private fun showList(request: Request) {
        val header = mutableListOf<String>()
        header.add("번호")
        header.add("제목".padEnd(17, ' '))
        header.add("내용".padEnd(17, ' '))
        header.add("제목")
        println(header.joinToString(" / "))

        articleService.getArticles().reversed().forEach {
            val row = mutableListOf<String>()
            row.add(it.id.toString().padEnd(4, ' '))
            row.add(it.regDate)
            row.add(it.updateDate)
            row.add(it.title)

            println(row.joinToString(" / "))
        }
    }

    private fun showAdd(request: Request) {
        print("제목 : ")
        val title = readLine()!!.trim()
        print("내용 : ")
        val body = readLine()!!.trim()

        doAdd(Request("usr/article/doAdd?title=$title&body=$body"))
    }

    private fun doAdd(request: Request) {
        val title = request.getParameter("title")!!
        val body = request.getParameter("body")!!

        val newId = articleService.addArticle(title, body)

        println("${newId}번 글이 생성되었습니다.")
    }
}

object App {
    fun run() {
        println("== 텍스트 게시판 시작 ==")

        val usrArticleController = Container.usrArticleController

        while (true) {
            print("명령어) ")
            val command = readLine()!!.trim()

            if (command.isNullOrEmpty())
                continue

            val request = Request(command)

            if (request.path.startsWith("usr/article")) {
                usrArticleController.callAction(request)
            } else if (command == "system exit") {
                break
            }
        }

        println("== 텍스트 게시판 끝 ==")
    }
}

class Request(private val rawCommand: String) {
    private val rawCommandBits by lazy {
        val path = rawCommand.substringBefore('?')
        val queryStr = rawCommand.substringAfter('?')

        arrayOf(path, queryStr)
    }

    val path by lazy {
        rawCommandBits[0]
    }

    private val queryString by lazy {
        rawCommandBits[1]
    }

    private val parameters by lazy {
        val queryStringBits = queryString.split("&")
        val parameters = mutableMapOf<String, String>()

        for (queryStringBit in queryStringBits) {
            val name = queryStringBit.substringBefore('=')
            val value = queryStringBit.substringAfter('=')

            parameters[name] = value
        }

        parameters
    }

    fun getParameter(name: String): String? = parameters[name]
}

object Util {
    fun getNowDateStr(): String {
        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return current.format(formatter)
    }
}