-- Migration V51: Ensure all existing tenant subscriptions have locked-in paid_amount
UPDATE tenant_subscription ts
SET paid_amount = CASE 
    WHEN ts.billing_cycle = 'YEARLY' AND p.yearly_price IS NOT NULL THEN p.yearly_price
    ELSE p.monthly_price
END
FROM subscription_plan p
WHERE ts.plan_id = p.id AND ts.paid_amount IS NULL;
