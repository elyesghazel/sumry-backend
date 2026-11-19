#!/usr/bin/env bash
# Fills a fresh Sumry instance with a made-up demo user and about three months of
# invented data, for screenshots and trying the app. Every name and number is fake.
#
#   SUMRY_URL=http://localhost ./demo-seed.sh
#
# Needs bash, curl, jq and GNU date, and a backend with SUMRY_REGISTRATION_MODE=open.
# Never point this at an instance with real data.
set -euo pipefail

URL="${SUMRY_URL:-http://localhost}"
API="$URL/api"
EMAIL="${DEMO_EMAIL:-demo@example.com}"
PASSWORD="${DEMO_PASSWORD:-demo-password-123}"
DAYS="${DEMO_DAYS:-90}"
RANDOM=42 # same data on every run

json() { curl -fsS -H 'content-type: application/json' -H "authorization: Bearer ${TOKEN:-}" "$@"; }
day() { date -u -d "$1 days ago" +%Y-%m-%d; }
when() { printf '%sT%02d:%02d:00Z\n' "$(day "$1")" $(( 6 + RANDOM % 14 )) $(( RANDOM % 60 )); }

echo "registering $EMAIL on $URL"
curl -fsS -o /dev/null -H 'content-type: application/json' "$API/users/register" \
  -d "$(jq -nc --arg e "$EMAIL" --arg p "$PASSWORD" '{username:"Demo", email:$e, password:$p}')" \
  2>/dev/null || echo "  (already registered, signing in)"
TOKEN=$(json "$API/users/login" -d "$(jq -nc --arg e "$EMAIL" --arg p "$PASSWORD" '{email:$e, password:$p}')" | jq -r .token)

account() { json "$API/users/accounts" -d "$(jq -nc --arg n "$1" --arg b "$2" --argjson s "$3" \
  '{accountName:$n, currentBalance:($b|tonumber), currency:"CHF", spending:$s}')" | jq -r .id; }
GIRO=$(account Giro 900 true)
SAVINGS=$(account Savings 8200 false)
CASH=$(account Cash 80 true)

category() { json -o /dev/null "$API/category/" -d "$(jq -nc --arg l "$1" --arg t "$2" --arg c "$3" --arg g "$4" \
  '{label:$l, categoryType:$t, color:$c, parentGroup:$g}')"; }
category Salary      INCOME  '#4caf7a' Income
category Side\ jobs  INCOME  '#7fc8a9' Income
category Rent        EXPENSE '#8a8f98' Home
category Groceries   EXPENSE '#d9a441' Food
category Eating\ out EXPENSE '#e07a5f' Food
category Transport   EXPENSE '#5b8def' Mobility
category Insurance   EXPENSE '#9b7fd1' Home
category Phone       EXPENSE '#6cc3d5' Home
category Subscriptions EXPENSE '#c96bb0' Leisure
category Leisure     EXPENSE '#f2c14e' Leisure
category Shopping    EXPENSE '#b08968' Shopping

rule() { json -o /dev/null "$API/rules" -d "$(jq -nc --arg p "$1" --arg c "$2" '{pattern:$p, categoryLabel:$c}')"; }
for p in Migros Coop Denner Lidl Aldi Bakery Kiosk Market; do rule "$p" Groceries; done
for p in Train Bus Parking Bike; do rule "$p" Transport; done
for p in Pizzeria Thai Kebab Burger Sushi Lunch Coffee; do rule "$p" "Eating out"; done
for p in Cinema Climbing Concert Bowling Books; do rule "$p" Leisure; done
rule streaming Subscriptions

tx() { # days-ago amount description category account
  json -o /dev/null "$API/transactions/" -d "$(jq -nc --arg a "$2" --arg d "$3" --arg c "$4" --arg acc "$5" --arg t "$(when "$1")" \
    '{amount:($a|tonumber), description:$d, categoryLabel:$c, userAccountId:$acc, date:$t}')"; }
transfer() { # days-ago amount description from to
  json -o /dev/null "$API/transactions/transfer" -d "$(jq -nc --arg a "$2" --arg d "$3" --arg f "$4" --arg to "$5" --arg t "$(when "$1")" \
    '{amount:($a|tonumber), description:$d, fromAccountId:$f, toAccountId:$to, date:$t}')"; }
pick() { local a=("$@"); echo "${a[RANDOM % ${#a[@]}]}"; }
price() { printf '%d.%02d' $(( $1 + RANDOM % ($2 - $1) )) $(( (RANDOM % 20) * 5 )); }

echo "booking $DAYS days of transactions"
for ((d = DAYS; d >= 0; d--)); do
  dom=$(date -u -d "$d days ago" +%-d)
  dow=$(date -u -d "$d days ago" +%u)
  case $dom in
    1)  tx $d 1650.00 "Rent Hofstrasse" Rent "$GIRO"
        tx $d 389.40 "Health insurance" Insurance "$GIRO" ;;
    3)  tx $d 39.00 "Mobile plan" Phone "$GIRO" ;;
    5)  tx $d 13.95 "Music streaming" Subscriptions "$GIRO" ;;
    12) tx $d 17.90 "Video streaming" Subscriptions "$GIRO" ;;
    26) tx $d 5120.00 "Salary Muster AG" Salary "$GIRO"
        transfer $d 600.00 "Monthly savings" "$GIRO" "$SAVINGS" ;;
  esac
  if [[ $dow == 2 || $dow == 6 ]]; then
    tx $d "$(price 28 118)" "$(pick Migros Coop Denner Lidl Aldi)" Groceries "$GIRO"
  fi
  if (( RANDOM % 4 == 0 )); then
    tx $d "$(price 6 24)" "$(pick Bakery Kiosk Market Coffee)" Groceries "$CASH"
  fi
  if [[ $dow == 5 ]] || (( RANDOM % 9 == 0 )); then
    tx $d "$(price 18 72)" "$(pick 'Pizzeria Roma' 'Thai Garden' 'Kebab Haus' 'Burger Bar' 'Sushi Ya' Lunch)" "Eating out" "$GIRO"
  fi
  if (( RANDOM % 6 == 0 )); then
    tx $d "$(price 4 15)" "$(pick 'Train ticket' 'Bus ticket' 'Bike repair' Parking)" Transport "$GIRO"
  fi
  if (( RANDOM % 11 == 0 )); then
    tx $d "$(price 25 140)" "$(pick 'Cinema' 'Climbing gym' 'Concert' 'Bowling' 'Books')" Leisure "$GIRO"
  fi
  if (( RANDOM % 14 == 0 )); then
    tx $d "$(price 30 190)" "$(pick 'Sports shop' 'Electronics' 'Clothing' 'Furniture store')" Shopping "$GIRO"
  fi
  if (( d % 30 == 9 )); then
    tx $d 180.00 "Tutoring" "Side jobs" "$GIRO"
  fi
  if (( d % 21 == 4 )); then
    transfer $d 100.00 "ATM" "$GIRO" "$CASH"
  fi
done

echo "budgets, planned items and a goal"
budget() { json -o /dev/null "$API/budgets" -d "$(jq -nc --arg c "$1" --arg m "$2" '{categoryLabel:$c, maxAmount:($m|tonumber), period:"MONTHLY", startDay:1}')"; }
budget Groceries 650
budget "Eating out" 220
budget Leisure 150
budget Shopping 200
budget Transport 80

next_month_day() { # day-of-month -> the next date with that day
  local today; today=$(date -u +%-d)
  if (( today < $1 )); then date -u +%Y-%m-$(printf %02d "$1")
  else date -u -d "$(date -u +%Y-%m-01) +1 month" +%Y-%m-$(printf %02d "$1"); fi
}
planned() { # name amount frequency start category account [to-account] [autoBook]
  json -o /dev/null "$API/planned" -d "$(jq -nc --arg n "$1" --arg a "$2" --arg f "$3" --arg s "$4" --arg c "$5" --arg acc "$6" --arg to "${7:-}" --argjson auto "${8:-false}" \
    '{name:$n, amount:($a|tonumber), frequency:$f, startDate:$s, accountId:$acc, autoBook:$auto}
     + (if $c == "" then {} else {categoryLabel:$c} end) + (if $to == "" then {} else {toAccountId:$to} end)')"; }
planned "Rent"             1650.00 MONTHLY "$(next_month_day 1)"  Rent          "$GIRO" "" true
planned "Health insurance"  389.40 MONTHLY "$(next_month_day 1)"  Insurance     "$GIRO" "" true
planned "Mobile plan"        39.00 MONTHLY "$(next_month_day 3)"  Phone         "$GIRO"
planned "Music streaming"    13.95 MONTHLY "$(next_month_day 5)"  Subscriptions "$GIRO"
planned "Video streaming"    17.90 MONTHLY "$(next_month_day 12)" Subscriptions "$GIRO"
planned "Salary"           5120.00 MONTHLY "$(next_month_day 26)" Salary        "$GIRO"
planned "Monthly savings"   600.00 MONTHLY "$(next_month_day 26)" ""            "$GIRO" "$SAVINGS"
planned "Half-fare travelcard" 190.00 YEARLY "$(date -u -d '+40 days' +%Y-%m-%d)" Transport "$GIRO"

json -o /dev/null "$API/goals" -d "$(jq -nc --arg t "$(date -u -d '+8 months' +%Y-%m-01)" \
  '{name:"Summer trip", targetAmount:3000, targetDate:$t, savedAmount:1250, color:"#5b8def"}')"
json -o /dev/null "$API/goals" -d '{"name":"New bike","targetAmount":1400,"savedAmount":420,"color":"#4caf7a"}'

# Giro and Savings were checked against the bank today; Cash still needs counting.
for id in "$GIRO" "$SAVINGS"; do
  balance=$(json "$API/users/accounts/id/$id" | jq -r .currentBalance)
  json -o /dev/null -X PUT "$API/users/accounts/reconcile/$id" -d "$(jq -nc --arg b "$balance" '{newBalance:($b|tonumber)}')"
done

echo "done: sign in at $URL as $EMAIL / $PASSWORD"
