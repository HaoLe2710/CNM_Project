alter table conversation_user_settings
    add column notification_level varchar(32) null,
    add column custom_name varchar(100) null;
