fun main(args: Array<String>) {
    println("== 텍스트 게시판 시작 ==")

    var articleLastId = 0

    while (true) {
        print("명령어) ")
        val command = readLine()?.trim()

        if (command.isNullOrEmpty())
            continue

        if (command == "article add") {
            val id = ++articleLastId
            print("제목 : ")
            val title = readLine()?.trim()
            print("내용 : ")
            val body = readLine()?.trim()

            println("== 입력된 게시물 ==")
            println(
                """
                번호 : ${id}
                제목 : ${title}
                내용 : ${body}
            """.trimIndent()
            )
        }
    }

    println("== 텍스트 게시판 끝 ==")
}