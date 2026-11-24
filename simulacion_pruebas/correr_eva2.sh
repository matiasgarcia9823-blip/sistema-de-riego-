#!/bin/sh
# Arma el APK y corre Hertzbench en pixel8 y en un segundo emulador.
# Uso, desde cualquier directorio, en Linux con KVM:
#   sh simulacion_pruebas/correr_eva2.sh nombre_del_segundo_avd
set -eu

raiz=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
cd "$raiz"

segundo=${1:-}
case "$segundo" in
  ""|-h|--help)
    echo "Uso: sh simulacion_pruebas/correr_eva2.sh <nombre-del-segundo-avd>" >&2
    exit 1
    ;;
esac
case "$segundo" in
  *[!A-Za-z0-9_.-]*)
    echo "El nombre del AVD solo puede llevar letras, numeros, punto, guion y guion bajo." >&2
    exit 1
    ;;
esac
if [ "$segundo" = "pixel8" ]; then
  echo "El segundo AVD tiene que ser distinto de pixel8." >&2
  exit 1
fi

if [ -x ./gradlew ]; then
  ./gradlew assembleDebug
else
  sh ./gradlew assembleDebug
fi

mkdir -p simulacion_pruebas/salidas

correr() {
  escenario=$1
  salida=$2
  hertzbench run "$escenario" >"$salida" 2>&1
  ultima=$(tail -n 1 "$salida" | tr -d '\r')
  echo "$salida: $ultima"
  if [ "$ultima" != "RESULT: PASS" ]; then
    echo "La corrida no termino en RESULT: PASS. Revisa $salida" >&2
    exit 1
  fi
}

correr firmware/eva2.yaml simulacion_pruebas/salidas/hertzbench_pixel8.txt

sed "s/^      avd: pixel8$/      avd: $segundo/" firmware/eva2.yaml \
  > "simulacion_pruebas/salidas/eva2-$segundo.yaml"
correr "simulacion_pruebas/salidas/eva2-$segundo.yaml" \
  "simulacion_pruebas/salidas/hertzbench_$segundo.txt"

echo "PRUEBAS OK"
