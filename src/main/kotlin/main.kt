import java.sql.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.properties.Delegates

class SecSql {
    private val sqlBuilder by lazy { StringBuilder() }

    private val dataList by lazy { mutableListOf<Any>() }

    private val format: String
        get() = sqlBuilder.toString().trim { it <= ' ' }

    private val rawSql: String
        get() {
            var rawSql = format
            for (i in dataList.indices) {
                val data = dataList[i]
                rawSql = rawSql.replaceFirst("\\?".toRegex(), "'$data'")
            }
            return rawSql
        }

    companion object {
        fun from(sql: String): SecSql {
            return SecSql().append(sql)
        }
    }

    override fun toString(): String {
        return "rawSql=$rawSql, data=$dataList"
    }

    private val isInsert: Boolean
        get() = format.startsWith("INSERT")

    fun append(vararg args: Any): SecSql {
        if (args.isNotEmpty()) {
            val sqlBit = args[0] as String
            sqlBuilder.append("$sqlBit ")
        }

        for (i in 1 until args.size) {
            dataList.add(args[i])
        }
        return this
    }

    fun getPreparedStatement(connection: Connection): PreparedStatement {
        val stmt = if (isInsert) {
            connection.prepareStatement(format, Statement.RETURN_GENERATED_KEYS)
        } else {
            connection.prepareStatement(format)
        }

        for (i in dataList.indices) {
            val data = dataList[i]
            val parameterIndex = i + 1
            if (data is Int) {
                stmt.setInt(parameterIndex, data)
            } else if (data is String) {
                stmt.setString(parameterIndex, data)
            }
        }

        return stmt
    }
}

class MySQLApi(
    private val dbHost: String,
    private val dbLoginId: String,
    private val dbLoginPw: String,
    private val dbName: String
) {
    private var isDevMode by Delegates.notNull<Boolean>()

    init {
        isDevMode = false
    }

    private val connections: MutableMap<Long, Connection> by lazy {
        mutableMapOf()
    }

    fun closeConnection() {
        val currentThreadId = Thread.currentThread().id
        if (!connections.containsKey(currentThreadId)) {
            return
        }

        val connection = connections[currentThreadId]

        if (!(connection == null || connection.isClosed)) {
            connection.close()
        }

        connections.remove(currentThreadId)
    }

    private val connection: Connection
        get() {
            val currentThreadId = Thread.currentThread().id

            if (!connections.containsKey(currentThreadId)) {
                Class.forName("com.mysql.cj.jdbc.Driver")
                val url = ("jdbc:mysql://" + dbHost + "/" + dbName
                        + "?useUnicode=true&characterEncoding=utf8&autoReconnect=true&serverTimezone=Asia/Seoul&useOldAliasMetadataBehavior=true&zeroDateTimeBehavior=convertToNull&connectTimeout=60")
                val connection = DriverManager.getConnection(url, dbLoginId, dbLoginPw)
                connections[currentThreadId] = connection
            }

            return connections[currentThreadId]!!
        }

    fun selectRow(sql: SecSql): Map<String, Any> {
        val rows = selectRows(sql)
        return if (rows.isEmpty()) {
            mapOf()
        } else rows[0]
    }

    fun selectRows(sql: SecSql): List<Map<String, Any>> {
        val stmt = sql.getPreparedStatement(connection)
        val rs = stmt.executeQuery()

        val metaData = rs.metaData
        val columnSize = metaData.columnCount

        val rows: MutableList<Map<String, Any>> = mutableListOf()

        while (rs.next()) {
            val row: MutableMap<String, Any> = HashMap()

            for (columnIndex in 0 until columnSize) {
                val columnName = metaData.getColumnName(columnIndex + 1)
                when (val value = rs.getObject(columnName)) {
                    is Long -> {
                        val numValue = value.toInt()
                        row[columnName] = numValue
                    }
                    is LocalDateTime -> {
                        row[columnName] = value.toString().replace('T', ' ')
                    }
                    is Timestamp -> {
                        var dateValue = value.toString()
                        dateValue = dateValue.substring(0, dateValue.length - 2)
                        row[columnName] = dateValue
                    }
                    else -> {
                        row[columnName] = value
                    }
                }
            }
            rows.add(row)
        }
        return rows
    }

    fun selectRowIntValue(sql: SecSql): Int {
        val row = selectRow(sql)
        for (key in row.keys) {
            return row[key] as Int
        }
        return -1
    }

    fun selectRowStringValue(sql: SecSql): String? {
        val row = selectRow(sql)
        for (key in row.keys) {
            return row[key] as String?
        }
        return ""
    }

    fun selectRowBooleanValue(sql: SecSql): Boolean {
        val row = selectRow(sql)
        for (key in row.keys) {
            return row[key] as Int == 1
        }
        return false
    }

    // 반환 : 생성된 ID를 반환
    fun insert(sql: SecSql): Int {
        val stmt = sql.getPreparedStatement(connection)
        stmt.executeUpdate()
        val rs = stmt.generatedKeys
        if (rs.next()) {
            return rs.getInt(1)
        }
        return -1
    }

    // 반환 : 수정된 row 개수
    fun update(sql: SecSql): Int {
        val stmt = sql.getPreparedStatement(connection)
        return stmt.executeUpdate()
    }

    // 반환 : 삭제된 row 개수
    fun delete(sql: SecSql): Int {
        return update(sql)
    }
}


fun main(args: Array<String>) {
    App.run()
}

data class Article(
    val id: Int,
    val regDate: String,
    val updateDate: String,
    val title: String,
    val body: String
) {
    constructor(it: Map<String, Any>) : this(
        it["id"] as Int,
        it["regDate"] as String,
        it["updateDate"] as String,
        it["title"] as String,
        it["body"] as String
    )
}

class ArticleDao {
    fun getArticles(): List<Article> {
        return Container.mysqlApi.selectRows(
            SecSql
                .from("SELECT * FROM article ORDER BY id DESC")
        ).map { Article(it) }
    }

    fun addArticle(title: String, body: String): Int {
        return Container.mysqlApi.insert(
            SecSql
                .from("INSERT INTO article")
                .append("SET regDate = NOW()")
                .append(", updateDate = NOW()")
                .append(", title = ?", title)
                .append(", body = ?", body)
        )
    }

    fun getArticleById(id: Int): Article? {
        val map = Container.mysqlApi.selectRow(
            SecSql
                .from("SELECT * FROM article")
                .append("WHERE id = ?", id)
        )

        if (map.isEmpty()) {
            return null
        }

        return Article(map)
    }

    fun deleteById(id: Int) {
        Container.mysqlApi.delete(
            SecSql
                .from("DELETE FROM article")
                .append("WHERE id = ?", id)
        )
    }

    fun modify(id: Int, title: String, body: String) {
        Container.mysqlApi.update(
            SecSql
                .from("UPDATE article")
                .append("SET updateDate = NOW()")
                .append(", title = ?", title)
                .append(", `body` = ?", body)
                .append("WHERE id = ?", id)
        )
    }
}

class ArticleService {
    private val articleDao = Container.articleDao


    fun addArticle(title: String, body: String): Int {
        return articleDao.addArticle(title, body)
    }

    fun getArticles() = articleDao.getArticles()

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

    val mysqlApi by lazy {
        MySQLApi(App.DB_HOST, App.DB_ID, App.DB_PW, App.DB_NAME)
    }
}

class UsrArticleController {
    private val articleService = Container.articleService

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
    val DB_HOST = "localhost"
    val DB_ID = "sbsst"
    val DB_PW = "sbs123414"
    val DB_NAME = "kotlin_text_board"

    fun run() {
        println("== 텍스트 게시판 시작 ==")

        val usrArticleController = Container.usrArticleController

        while (true) {
            print("명령어) ")
            val rawCommand = readLine()!!.trim()

            if (rawCommand.isEmpty())
                continue

            val command = if (rawCommand.startsWith('/')) {
                rawCommand.substring(1)
            } else {
                rawCommand
            }

            val request = Request(command)

            if (request.path.startsWith("usr/article")) {
                usrArticleController.callAction(request)
            } else if (command == "system/exit") {
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

    fun getParameter(name: String) = parameters[name]
}

object Util {
    fun getNowDateStr(): String {
        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return current.format(formatter)
    }
}