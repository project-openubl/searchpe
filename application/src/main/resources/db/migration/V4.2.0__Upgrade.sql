create sequence basic_user_sequence start with 1 increment by 50;
create sequence version_sequence start with 1 increment by 50;

-- To not collide with previous IDs if we upgrade an existing DB
select setval('basic_user_sequence', (select count(id) + 1 FROM basic_user));
select setval('version_sequence', (select count(id) + 1 FROM version));
