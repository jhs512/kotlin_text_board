fun main(args: Array<String>) {
    println("== 텍스트 게시판 시작 ==")

    while (true) {
        print("명령어) ")
        val command = readLine()?.trim()

        if (command.isNullOrEmpty())
            continue

        println("입력된 명령어 : ${command}")
    }

    println("== 텍스트 게시판 끝 ==")
}