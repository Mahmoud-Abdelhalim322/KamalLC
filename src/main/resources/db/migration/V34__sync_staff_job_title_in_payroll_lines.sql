-- V34: Sync job_title from staff to staff_payroll_line for lines where job_title is null
UPDATE staff_payroll_line spl
SET job_title = s.job_title
FROM staff s
WHERE spl.staff_id = s.id AND spl.job_title IS NULL AND s.job_title IS NOT NULL;
