CREATE OR REPLACE FUNCTION public.resolve_price(p_item_type text, p_item_id uuid, p_country text)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  v_base numeric; v_base_cur text; v_country text := upper(coalesce(p_country,''));
  v_is_free boolean := false;
  cp record; ov record; v_price numeric; v_cur text; v_rule jsonb;
begin
  if p_item_type = 'COURSE' then
    select base_price, base_currency, coalesce(is_free, false)
      into v_base, v_base_cur, v_is_free
      from public.courses where id = p_item_id;
  elsif p_item_type = 'LIVE_PLAN' then
    select p.base_price, s.base_currency into v_base, v_base_cur
    from public.live_plans p join public.live_services s on s.id = p.live_service_id where p.id = p_item_id;
  else
    raise exception 'UNKNOWN_ITEM_TYPE';
  end if;
  if v_base is null then raise exception 'ITEM_NOT_FOUND'; end if;
  v_base_cur := coalesce(v_base_cur, public.setting_text('pricing.base_currency','USD'));

  -- A course flagged free is free, full stop — regardless of whatever base_price happens to be
  -- stored (e.g. a leftover value from before the course was marked free). This is the single
  -- place price is resolved for checkout, so this guarantee holds no matter what UI or bug wrote
  -- the row.
  if p_item_type = 'COURSE' and v_is_free then
    return jsonb_build_object(
      'base_price', 0, 'base_currency', v_base_cur,
      'price', 0, 'currency', v_base_cur,
      'country_code', v_country, 'pricing_rule', jsonb_build_object('source','free_course','country',v_country)
    );
  end if;

  select * into ov from public.price_overrides
   where item_type = p_item_type and item_id = p_item_id and is_active
     and (country_code = v_country or country_code is null)
   order by (country_code is not null) desc limit 1;

  if found then
    v_price := ov.price; v_cur := ov.currency;
    v_rule := jsonb_build_object('source','override','override_id',ov.id,'country',v_country);
  else
    select * into cp from public.country_pricing where country_code = v_country and is_active;
    if found then
      v_price := v_base * cp.fx_multiplier;
      v_price := v_price * (1 - cp.discount_percent/100.0);
      if cp.round_to > 0 then v_price := round(v_price / cp.round_to) * cp.round_to; end if;
      v_cur := cp.currency;
      v_rule := jsonb_build_object('source','country_pricing','country',v_country,
        'fx_multiplier',cp.fx_multiplier,'discount_percent',cp.discount_percent,'region_group',cp.region_group);
    else
      v_price := v_base; v_cur := v_base_cur;
      v_rule := jsonb_build_object('source','global_default','country',v_country);
    end if;
  end if;

  return jsonb_build_object(
    'base_price', round(v_base,2), 'base_currency', v_base_cur,
    'price', round(greatest(v_price,0),2), 'currency', v_cur,
    'country_code', v_country, 'pricing_rule', v_rule
  );
end $function$;;
