-- V63: Move appearance settings that still hold the old seeded defaults to the premium
-- brand-yellow theme (white sidebar, yellow active pill, yellow hero band, ink text).
-- Only rows still on the exact seeded value change, so a tenant that picked its own
-- colours keeps them.

UPDATE app_setting SET value = '#FFFFFF', updated_at = NOW()
WHERE setting_key = 'THEME_SIDEBAR_BG' AND effective_to IS NULL AND UPPER(value) = '#F5B000';

UPDATE app_setting SET value = '#F5B000', updated_at = NOW()
WHERE setting_key = 'THEME_SIDEBAR_ACTIVE_BG' AND effective_to IS NULL AND UPPER(value) = '#D98E04';

UPDATE app_setting SET value = '#16171B', updated_at = NOW()
WHERE setting_key = 'THEME_SIDEBAR_TEXT_COLOR' AND effective_to IS NULL AND UPPER(value) = '#000000';
