from backend.app.fixed_stream_research import select_subscription, parse_ltp_frame, MAX_SUBSCRIPTION

def test_subscription_is_explicitly_capped_and_cash_only():
    rows = [{"exchange":"NSE","segment":"CASH","exchange_token":str(i)}
            for i in range(1_100)]
    rows += [{"exchange":"NSE","segment":"FNO","exchange_token":"2000"}]
    subscription = select_subscription(rows)
    assert len(subscription) == MAX_SUBSCRIPTION
    assert subscription[0]["exchange_token"] == "0"
    assert all(x["segment"] == "CASH" for x in subscription)

def test_source_timestamp_is_required_and_stale_updates_are_excluded():
    now = 1_760_000_000_000
    raw = {"ltp":{"NSE":{"CASH":{
        "11":{"ltp":100.0,"tsInMillis":now-500},
        "12":{"ltp":101.0,"tsInMillis":now-11_000},
        "13":{"ltp":0.0,"tsInMillis":now-100},
        "14":{"ltp":102.0},
        "15":{"ltp":103.0,"tsInMillis":now+100},
    }}}}
    result = parse_ltp_frame(raw, {str(i):f"STOCK{i}" for i in range(11,16)}, now)
    assert [r.symbol for r in result] == ["STOCK11", "STOCK15"]
    assert result[0].source_age_ms == 500

def test_stream_cannot_guess_unlabelled_symbols():
    raw = {"ltp":{"NSE":{"CASH":{"10":{"ltp":123,"tsInMillis":1000}}}}}
    assert parse_ltp_frame(raw, {}, 1200) == []
