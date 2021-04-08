DROP DATABASE IF EXISTS kotlin_text_board;
CREATE DATABASE kotlin_text_board;
USE kotlin_text_board;

CREATE TABLE article (
	id INT(10) UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT,
	regDate DATETIME NOT NULL,
	updateDate DATETIME NOT NULL,
    title CHAR(100) NOT NULL,
    `body` TEXT NOT NULL
);
