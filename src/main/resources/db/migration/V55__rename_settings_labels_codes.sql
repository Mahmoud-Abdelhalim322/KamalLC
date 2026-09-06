-- Replace Arabic type names in settings labels with their code equivalents
-- كفالة → KSC, فريلانس → KSCFL

UPDATE app_setting SET label_ar = REPLACE(label_ar, 'كفالة', 'KSC')
WHERE label_ar LIKE '%كفالة%';

UPDATE app_setting SET label_ar = REPLACE(label_ar, 'فريلانس', 'KSCFL')
WHERE label_ar LIKE '%فريلانس%';
