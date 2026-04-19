alter table conversations
    add column background_type varchar(16) null,
    add column background_color varchar(32) null,
    add column background_image_url varchar(500) null;

update conversations c
set background_type = s.background_type,
    background_color = s.background_color,
    background_image_url = s.background_image_url
from (
    select distinct on (conversation_id)
        conversation_id,
        background_type,
        background_color,
        background_image_url
    from conversation_user_settings
    where background_type is not null
       or background_color is not null
       or background_image_url is not null
    order by conversation_id, coalesce(updated_at, created_at) desc
) s
where c.id = s.conversation_id
  and (c.background_type is null and c.background_color is null and c.background_image_url is null);
