INSERT INTO actors (name) VALUES ('Henry Cavill');
INSERT INTO actors (name) VALUES ('Gal Godot');
INSERT INTO actors (name) VALUES ('Ezra Miller');
INSERT INTO actors (name) VALUES ('Ben Affleck');
INSERT INTO actors (name) VALUES ('Ray Fisher');
INSERT INTO actors (name) VALUES ('Jason Momoa');
INSERT INTO actors (name) VALUES ('Sigourney Weaver');
COMMIT;

-- Directors
INSERT INTO directors (name, last_name) VALUES ('Zack', 'Snyder');
INSERT INTO directors (name, last_name) VALUES ('Ridley', 'Scott');
COMMIT;

-- Movies
INSERT INTO movies (id, title, year_of_production, director_id)
VALUES ('5e5a39bb-a497-4432-93e8-7322f16ac0b2', 'Zack Snyder''s Justice League', '2021', 1);
INSERT INTO movies (id, title, year_of_production, director_id)
VALUES ('2710d272-8d76-11ec-b909-0242ac120002', 'Alien', '1979', 2);
COMMIT;

-- Actor-Movie link
INSERT INTO movies_actors (movie_id, actor_id)
VALUES ('5e5a39bb-a497-4432-93e8-7322f16ac0b2', 1);
INSERT INTO movies_actors (movie_id, actor_id)
VALUES ('5e5a39bb-a497-4432-93e8-7322f16ac0b2', 2);
INSERT INTO movies_actors (movie_id, actor_id)
VALUES ('5e5a39bb-a497-4432-93e8-7322f16ac0b2', 3);
INSERT INTO movies_actors (movie_id, actor_id)
VALUES ('5e5a39bb-a497-4432-93e8-7322f16ac0b2', 4);
INSERT INTO movies_actors (movie_id, actor_id)
VALUES ('5e5a39bb-a497-4432-93e8-7322f16ac0b2', 5);
INSERT INTO movies_actors (movie_id, actor_id)
VALUES ('5e5a39bb-a497-4432-93e8-7322f16ac0b2', 6);
INSERT INTO movies_actors (movie_id, actor_id)
VALUES ('2710d272-8d76-11ec-b909-0242ac120002', 7);
COMMIT;