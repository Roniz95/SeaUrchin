DROP DATABASE seaUrchin;
CREATE DATABASE IF NOT exists seaUrchin;
USE SeaUrchin;

CREATE TABLE IF NOT exists herd(
	herdId INT,
    lat FLOAT (10, 6) NOT NULL,
    lon FLOAT (10, 6) NOT NULL,
    PRIMARY KEY (herdId)
    );

CREATE TABLE IF NOT exists devices(
	macAddr VARCHAR(16),
    deviceType VARCHAR(100) DEFAULT ("buoyRepeater"),
    herdId INT NOT NULL,
    PRIMARY KEY (macAddr),
    FOREIGN KEY (herdId) REFERENCES herd(herdId)
    ON DELETE CASCADE
    ON UPDATE CASCADE
    );
    

CREATE TABLE IF NOT exists datagram(
	deviceMacAddr VARCHAR(16),
    recordTime DATETIME NOT NULL,
    herdId INT NOT NULL,
    temperature FLOAT (10, 6) NOT NULL,
    turbidity FLOAT (10, 6) NOT NULL,
    PRIMARY KEY (deviceMacAddr, recordTime),
    FOREIGN KEY (deviceMacAddr) REFERENCES devices(macAddr)
    ON DELETE NO ACTION 
    ON UPDATE NO ACTION
    );
    
INSERT INTO herd VALUES (1, 10.5, 12.3);
INSERT INTO devices VALUES ("abcd", "sensorArray", 1);
    