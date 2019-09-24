USE test;

drop table if exists `schedulers_lock`;

CREATE TABLE IF NOT EXISTS `schedulers_lock` (
  `owner_id` CHAR(50) NOT NULL,
  `resource_name` CHAR(50) NOT NULL,

  `locked_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `lock_until` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (`owner_id`, `resource_name`)
);

CREATE INDEX `resource_name_idx` ON  `schedulers_lock`(`resource_name`);