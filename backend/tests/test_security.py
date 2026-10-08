import hashlib
import hmac
from backend.app.security import canonical_message, verify_signature


def test_hmac_signature_roundtrip():
    secret = "x" * 32
    body = b'{"hello":"world"}'
    message = canonical_message("POST", "/v1/gateway/orders", "1700000000", "nonce-1234567890", body)
    signature = hmac.new(secret.encode(), message, hashlib.sha256).hexdigest()
    assert verify_signature(secret, message, signature)
    assert not verify_signature(secret, message, "0" * 64)
