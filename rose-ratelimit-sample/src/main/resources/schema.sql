CREATE TABLE sample_report (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);

INSERT INTO sample_report (id, name) VALUES (1, 'alpha'), (2, 'beta'), (3, 'gamma');
