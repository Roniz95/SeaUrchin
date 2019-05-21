DROP DATABASE seaUrchin;
CREATE DATABASE IF NOT exists seaUrchin;
USE SeaUrchin;

CREATE TABLE IF NOT exists herd(
	herdId INT AUTO_INCREMENT,
    lat FLOAT (10, 6) NOT NULL,
    lon FLOAT (10, 6) NOT NULL,
    PRIMARY KEY (herdId)
    );

CREATE TABLE IF NOT exists BuoyRepeater(
	macAddr VARCHAR(16),
    deviceType VARCHAR(100) DEFAULT ("buoyRepeater"),
    herdId INT NOT NULL,
    PRIMARY KEY (macAddr),
    FOREIGN KEY (herdId) REFERENCES herd(herdId)
    ON DELETE CASCADE
    ON UPDATE CASCADE
    );
    

CREATE TABLE IF NOT exists seaUrchinDevice(
	macAddr VARCHAR(16) NOT NULL,
    deviceType VARCHAR(100) DEFAULT  ("sensorArray"),
    herdId INT NOT NULL,
    PRIMARY KEY (macAddr),
    FOREIGN KEY (herdId) REFERENCES herd(herdId)
    ON DELETE CASCADE
    ON UPDATE CASCADE
    );

CREATE TABLE IF NOT exists datagram(
	deviceMacAddr VARCHAR(16),
    herdId INT NOT NULL,
    recordTime TIMESTAMP NOT NULL,
    temperature FLOAT (10, 6) NOT NULL,
    turbidity FLOAT (10, 6) NOT NULL,
    PRIMARY KEY (deviceMacAddr, recordTime),
    FOREIGN KEY (deviceMacAddr) REFERENCES seaUrchinDevice(macAddr)
    ON DELETE NO ACTION 
    ON UPDATE NO ACTION
    );
    