#!/usr/bin/env bash
# =============================================================================
# 7PRO — تجهيز سيرفر اجتماعات Jitsi على VPS (Contabo أو Hetzner أو غيرهم)
#
# الاستخدام (على السيرفر نفسه، كـ root، Ubuntu 22.04 أو 24.04):
#   bash install-jitsi.sh meet.example.com you@example.com
#
# اختياري — سيرفر مفتوح بدون JWT (مش مُفضّل):
#   OPEN_SERVER=1 bash install-jitsi.sh meet.example.com you@example.com
#
# في الآخر بيطبعلك: الدومين + JWT App ID + JWT Secret — دول اللي هتحطهم في
# التطبيق: لوحة المالك ← سيرفرات الاجتماعات ← سيرفر Contabo ← تعديل.
# =============================================================================
set -euo pipefail

DOMAIN="${1:-}"
EMAIL="${2:-}"
OPEN_SERVER="${OPEN_SERVER:-0}"
JWT_APP_ID="${JWT_APP_ID:-7pro}"
JWT_APP_SECRET="${JWT_APP_SECRET:-}"
INSTALL_DIR="/opt/7pro-jitsi"

if [[ -z "$DOMAIN" || -z "$EMAIL" ]]; then
  echo "الاستخدام: bash install-jitsi.sh <الدومين> <إيميلك>"
  echo "مثال:     bash install-jitsi.sh meet.7pro.app 7pro7777@gmail.com"
  exit 1
fi
if [[ $EUID -ne 0 ]]; then echo "شغّل السكريبت كـ root (sudo -i)"; exit 1; fi

PUBLIC_IP="$(curl -4 -fsS https://api.ipify.org || hostname -I | awk '{print $1}')"
echo "==> IP السيرفر: $PUBLIC_IP"

# الدومين لازم يكون بيشاور على السيرفر قبل شهادة Let's Encrypt
DNS_IP="$(getent ahostsv4 "$DOMAIN" | awk 'NR==1{print $1}' || true)"
if [[ "$DNS_IP" != "$PUBLIC_IP" ]]; then
  echo "!! الدومين $DOMAIN بيشاور على '${DNS_IP:-لا شيء}' مش على $PUBLIC_IP"
  echo "!! اعمل A record للدومين على $PUBLIC_IP واستنى دقايق وبعدين شغّل السكريبت تاني."
  exit 1
fi

echo "==> تحديث النظام وتسطيب الأدوات"
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y curl wget unzip ufw ca-certificates openssl

if ! command -v docker >/dev/null 2>&1; then
  echo "==> تسطيب Docker"
  curl -fsSL https://get.docker.com | sh
fi
systemctl enable --now docker

echo "==> فتح البورتات: 22 و80 و443 (TCP) و10000 (UDP للصوت والفيديو)"
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 10000/udp
ufw --force enable

echo "==> تنزيل آخر نسخة من docker-jitsi-meet"
mkdir -p "$INSTALL_DIR"
cd "$INSTALL_DIR"
if [[ ! -f docker-compose.yml ]]; then
  URL="$(curl -fsS https://api.github.com/repos/jitsi/docker-jitsi-meet/releases/latest | grep '"zipball_url"' | cut -d '"' -f4)"
  wget -q -O jitsi.zip "$URL"
  unzip -q jitsi.zip
  SRC="$(find . -maxdepth 1 -type d -name 'jitsi-docker-jitsi-meet-*' | head -1)"
  cp -a "$SRC"/. .
  rm -rf "$SRC" jitsi.zip
fi

[[ -f .env ]] || cp env.example .env
./gen-passwords.sh >/dev/null

set_env() { # set_env KEY VALUE — يعدّل السطر لو موجود (حتى لو متعلّق عليه) أو يضيفه
  local key="$1" val="$2"
  if grep -qE "^#?\s*${key}=" .env; then
    sed -i -E "s|^#?\s*${key}=.*|${key}=${val}|" .env
  else
    echo "${key}=${val}" >> .env
  fi
}

set_env CONFIG "$INSTALL_DIR/config"
set_env HTTP_PORT 80
set_env HTTPS_PORT 443
set_env TZ Africa/Cairo
set_env PUBLIC_URL "https://$DOMAIN"
set_env JVB_ADVERTISE_IPS "$PUBLIC_IP"
set_env ENABLE_LETSENCRYPT 1
set_env LETSENCRYPT_DOMAIN "$DOMAIN"
set_env LETSENCRYPT_EMAIL "$EMAIL"
set_env ENABLE_HTTP_REDIRECT 1
# 7PRO شال التسجيل من المنتج، وصفحة التحضير بتتقفل من التطبيق
set_env ENABLE_RECORDING 0
set_env ENABLE_PREJOIN_PAGE 0
set_env ENABLE_WELCOME_PAGE 0
set_env ENABLE_LOBBY 0
set_env ENABLE_P2P 0

if [[ "$OPEN_SERVER" == "1" ]]; then
  set_env ENABLE_AUTH 0
  JWT_APP_ID=""
  JWT_APP_SECRET=""
else
  [[ -n "$JWT_APP_SECRET" ]] || JWT_APP_SECRET="$(openssl rand -hex 32)"
  set_env ENABLE_AUTH 1
  set_env AUTH_TYPE jwt
  set_env JWT_APP_ID "$JWT_APP_ID"
  set_env JWT_APP_SECRET "$JWT_APP_SECRET"
  set_env JWT_ACCEPTED_ISSUERS "$JWT_APP_ID"
  set_env JWT_ACCEPTED_AUDIENCES "$JWT_APP_ID"
  # الطالب اللي التوكن بتاعه اتأخر يدخل كضيف بعد ما المعلم يفتح الحصة، بدل ما يتقفل برّه
  set_env ENABLE_GUESTS 1
fi

mkdir -p config/{web,transcripts,prosody/config,prosody/prosody-plugins-custom,jicofo,jvb,jigasi,jibri}

echo "==> تشغيل Jitsi"
docker compose pull
docker compose up -d

echo "==> استنى الشهادة (لحد دقيقتين)…"
for i in $(seq 1 24); do
  if curl -fsS "https://$DOMAIN/config.js" >/dev/null 2>&1; then OK=1; break; fi
  sleep 5
done

echo
echo "================================================================"
if [[ "${OK:-0}" == "1" ]]; then
  echo "✅ السيرفر شغال: https://$DOMAIN"
else
  echo "⚠️  السيرفر اشتغل بس HTTPS لسه مش بيرد. راجع: cd $INSTALL_DIR && docker compose logs web"
fi
echo
echo "حط الكلام ده في التطبيق ← لوحة المالك ← سيرفرات الاجتماعات ← سيرفر Contabo ← تعديل:"
echo "  الدومين      : $DOMAIN"
if [[ -n "$JWT_APP_ID" ]]; then
  echo "  JWT App ID   : $JWT_APP_ID"
  echo "  JWT Secret   : $JWT_APP_SECRET"
  echo
  echo "احتفظ بالـ Secret في مكان آمن. موجود كمان في $INSTALL_DIR/.env"
else
  echo "  (سيرفر مفتوح — سيب خانات الـ JWT فاضية)"
fi
echo "وبعدين: اختبار ← فعّله."
echo "================================================================"
