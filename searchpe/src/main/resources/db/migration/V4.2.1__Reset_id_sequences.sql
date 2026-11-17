-- V4.2.0 seeded the id sequences with count(id) + 1. Versions are deleted every day, so count(id) is usually far below
-- max(id), and new ids collided with existing rows. Move each sequence past the highest id, never backwards.
-- The extra 50 (the sequence increment and Hibernate's allocation size) is a safety margin, so new ids stay above
-- max(id) whichever Hibernate optimizer interprets the sequence value.
select setval('version_sequence',
              greatest((select coalesce(max(id), 0) from version) + 50, (select last_value from version_sequence)));
select setval('basic_user_sequence',
              greatest((select coalesce(max(id), 0) from basic_user) + 50, (select last_value from basic_user_sequence)));
