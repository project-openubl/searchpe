insert into BASIC_USER(id, username, password, role, version)
values (nextval('hibernate_sequence'), 'admin', '$2a$10$Vu8TWiCj.qO7l8XTM8JYqOPjfF5Y4f/HbWvQwkIMA.EOtYS3ziddC', 'admin',
        1),
       (nextval('hibernate_sequence'), 'alice', '$2a$10$kycqeNLdoIhDgn5oegz.ie1QsUWD5l0cCb.4.0iDKp9x1NUjrwyAG', 'user',
        1);
