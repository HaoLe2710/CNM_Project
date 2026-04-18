alter table conversation_user_settings
    add column background_type varchar(16) null,
    add column background_color varchar(32) null,
    add column background_image_url varchar(500) null;
