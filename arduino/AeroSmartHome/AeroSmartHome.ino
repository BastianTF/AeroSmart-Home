/*
 * AeroSmart Home - Wemos D1 mini (ESP8266)
 *
 * Sensores: DHT11 (temperatura y humedad en D5/GPIO14) y PIR HC-SR501 (presencia en D6/GPIO12).
 * Actuadores: Servo SG90 (compuerta en D7/GPIO13) y Turbina (transistor/MOSFET en RX/GPIO3).
 *
 * Comunicación con la app: La placa crea su propia red Wi-Fi WPA2 (AP_SSID) y escucha en el puerto 3333.
 * Transmite seguridad mediante HMAC-SHA256 utilizando la clave en secrets.h.
 *
 * Sin app conectada (3.1.1.4): guarda una lectura cada 30 s en un búfer circular en RAM (120 = 1 hora)
 * y, cuando la app vuelve a conectarse, las reenvía una por una como "HIST:edad_s,temp,hum,pir,compuerta".
 */

#include <ESP8266WiFi.h>
#include <Servo.h>
#include <DHT.h>
#include <bearssl/bearssl.h>
#include "secrets.h"

// ---------- Pines ----------
const uint8_t PIN_DHT   = 14;   // D5 (GPIO14)
const uint8_t PIN_PIR   = 12;   // D6 (GPIO12)
const uint8_t PIN_SERVO = 13;   // D7 (GPIO13)
const uint8_t PIN_FAN   = 3;    // RX (GPIO3)

// ---------- Umbrales de humedad y Servo ----------
const float HUMEDAD_MAX = 65.0; // Abre compuerta en modo auto
const float HUMEDAD_MIN = 55.0; // Cierra compuerta en modo auto

const int SERVO_CLOSED = 0;     // Ángulo cerrado
const int SERVO_OPEN   = 180;   // Ángulo abierto
const int SERVO_PULSO_MIN = 500;   // µs
const int SERVO_PULSO_MAX = 2400;  // µs

// ---------- Ajustes de red y protocolo ----------
const uint16_t PORT = 3333;
const unsigned long DATA_EVERY_MS = 3000;
const unsigned long LINK_TIMEOUT_MS = 15000;
const size_t MAX_LINE = 120;

// ---------- Búfer offline (sin app conectada) ----------
const unsigned long OFFLINE_EVERY_MS = 30000; // una lectura cada 30 s
const uint8_t OFFLINE_SIZE = 120;             // 120 x 30 s = 1 hora; ~1 KB de RAM
const unsigned long HIST_EVERY_MS = 50;       // ritmo de reenvío para no saturar el enlace

// ---------- Energía ----------
const unsigned long LOOP_DELAY_ACTIVE_MS = 5;  // con app conectada: respuesta rápida
const unsigned long LOOP_DELAY_IDLE_MS   = 50; // sin app: el CPU descansa más (menos consumo)

WiFiServer server(PORT);
WiFiClient client;
DHT dht(PIN_DHT, DHT11);
Servo servo;

uint8_t key[32];
size_t keyLen = 0;
char appNonce[17] = "";
char devNonce[17] = "";
bool session = false;
uint32_t txSeq = 0, rxSeq = 0;
unsigned long lastRxMs = 0, lastDataMs = 0;
uint32_t rejected = 0;

// ---------- Estado del sistema ----------
bool ventOpen = false;
bool movement = false;
bool autoMode = true; // Modo automático por defecto
uint8_t fanPct = 60;
int temperature = 0, humidity = 0;
bool dhtOk = false;

struct OfflineReading { unsigned long ms; int8_t t; uint8_t h; uint8_t flags; };
OfflineReading offline[OFFLINE_SIZE];
uint8_t offHead = 0, offCount = 0;      // offHead = posición de la más antigua
unsigned long lastOfflineMs = 0, lastHistMs = 0;
uint32_t offDropped = 0;                // lecturas sobreescritas por búfer lleno

char line[MAX_LINE + 1];
size_t lineLen = 0;
bool overflow = false;

// ---------- Utilidades de formato ----------

bool isHexChar(char c) { return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'); }

bool isHexStr(const char *s, size_t len) {
  if (strlen(s) != len) return false;
  for (size_t i = 0; i < len; i++) if (!isHexChar(s[i])) return false;
  return true;
}

uint8_t hexVal(char c) {
  if (c >= '0' && c <= '9') return c - '0';
  if (c >= 'a' && c <= 'f') return c - 'a' + 10;
  return c - 'A' + 10;
}

bool isValidBody(const char *b) {
  const char *colon = strchr(b, ':');
  if (!colon || colon == b) return false;
  for (const char *p = b; p < colon; p++) if (*p < 'A' || *p > 'Z') return false;
  size_t n = 0;
  for (const char *p = colon + 1; *p; p++, n++) {
    char c = *p;
    bool ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == ',' || c == '.' || c == '-';
    if (!ok || n >= 60) return false;
  }
  return true;
}

void computeMac(char dir, const char *s1, const char *s2, uint32_t seq, const char *body, char out[17]) {
  br_hmac_key_context kc;
  br_hmac_key_init(&kc, &br_sha256_vtable, key, keyLen);
  br_hmac_context ctx;
  br_hmac_init(&ctx, &kc, 0);
  char seqStr[12];
  snprintf(seqStr, sizeof(seqStr), "%lu", (unsigned long)seq);
  br_hmac_update(&ctx, &dir, 1);
  br_hmac_update(&ctx, "|", 1);
  br_hmac_update(&ctx, s1, strlen(s1));
  br_hmac_update(&ctx, s2, strlen(s2));
  br_hmac_update(&ctx, "|", 1);
  br_hmac_update(&ctx, seqStr, strlen(seqStr));
  br_hmac_update(&ctx, "|", 1);
  br_hmac_update(&ctx, body, strlen(body));
  uint8_t mac[32];
  br_hmac_out(&ctx, mac);
  for (int i = 0; i < 8; i++) sprintf(out + 2 * i, "%02x", mac[i]);
  out[16] = '\0';
}

bool macEquals(const char *a, const char *b) {
  uint8_t diff = 0;
  for (int i = 0; i < 16; i++) {
    char x = a[i], y = b[i];
    if (x >= 'A' && x <= 'F') x += 32;
    diff |= (uint8_t)(x ^ y);
  }
  return diff == 0;
}

void sendFrame(uint32_t seq, const char *body) {
  if (!client || !client.connected()) return;
  char mac[17];
  computeMac('D', appNonce, devNonce, seq, body, mac);
  char out[MAX_LINE + 2];
  int n = snprintf(out, sizeof(out), "%lu|%s|%s\n", (unsigned long)seq, body, mac);
  if (n > 0 && (size_t)n < sizeof(out)) client.write((const uint8_t *)out, n);
}

void send(const char *body) { if (session) sendFrame(++txSeq, body); }

// ---------- Actuadores y Control Automático ----------

void applyOutputs() {
  servo.attach(PIN_SERVO, SERVO_PULSO_MIN, SERVO_PULSO_MAX);
  servo.write(ventOpen ? SERVO_OPEN : SERVO_CLOSED);
  delay(350);
  servo.detach(); // Detener impulsos PWM para ahorrar energía cuando el servo no está moviéndose
  analogWrite(PIN_FAN, ventOpen ? map(fanPct, 0, 100, 0, 1023) : 0);
}

void sendData() {
  if (!dhtOk) { send("ERR:DHT"); return; }
  char body[48];
  snprintf(body, sizeof(body), "DATA:%d,%d,%d,%d,%d", temperature, humidity, movement ? 1 : 0, ventOpen ? 1 : 0, ventOpen ? fanPct : 0);
  send(body);
}

// ---------- Búfer offline ----------

void storeOffline() {
  if (!dhtOk) return;
  uint8_t idx = (offHead + offCount) % OFFLINE_SIZE;
  if (offCount == OFFLINE_SIZE) { offHead = (offHead + 1) % OFFLINE_SIZE; offDropped++; } else offCount++;
  offline[idx] = { millis(), (int8_t)temperature, (uint8_t)humidity, (uint8_t)((movement ? 1 : 0) | (ventOpen ? 2 : 0)) };
}

/** Envía la lectura guardada más antigua. Se llama de a una por vuelta del loop. */
void flushOneOffline() {
  if (!session || offCount == 0) return;
  OfflineReading &r = offline[offHead];
  char body[48];
  snprintf(body, sizeof(body), "HIST:%lu,%d,%d,%d,%d", (millis() - r.ms) / 1000UL, r.t, r.h, r.flags & 1, (r.flags >> 1) & 1);
  send(body);
  offHead = (offHead + 1) % OFFLINE_SIZE;
  offCount--;
  if (offCount == 0) Serial.println(F("[offline] historial reenviado a la app"));
}

void processAutoMode() {
  if (!autoMode) return;

  bool changed = false;
  if (movement) {
    // Regla 1: Movimiento detectado -> cerrar compuerta inmediatamente
    if (ventOpen) {
      ventOpen = false;
      changed = true;
    }
  } else if (dhtOk) {
    // Regla 2: Sin movimiento -> controlar compuerta por humedad
    if (humidity >= HUMEDAD_MAX && !ventOpen) {
      ventOpen = true;
      changed = true;
    } else if (humidity <= HUMEDAD_MIN && ventOpen) {
      ventOpen = false;
      changed = true;
    }
  }

  if (changed) {
    applyOutputs();
    sendData();
  }
}

void failSafe() {
  ventOpen = false;
  applyOutputs();
  session = false;
  appNonce[0] = devNonce[0] = '\0';
  Serial.println(F("[enlace] sin datos de la app: compuerta cerrada"));
}

// ---------- Procesamiento de Comandos ----------

void handleHello(const char *body, uint32_t seq, const char *mac) {
  const char *nonce = body + 6;
  if (seq != 0 || !isHexStr(nonce, 16)) { rejected++; return; }
  char expected[17];
  computeMac('A', nonce, "", 0, body, expected);
  if (!macEquals(mac, expected)) { rejected++; Serial.println(F("[enlace] HELLO con clave incorrecta")); return; }

  strncpy(appNonce, nonce, 16); appNonce[16] = '\0';
  uint8_t rnd[8];
  ESP.random(rnd, sizeof(rnd));
  for (int i = 0; i < 8; i++) sprintf(devNonce + 2 * i, "%02x", rnd[i]);
  devNonce[16] = '\0';
  txSeq = 0; rxSeq = 0;
  session = true;
  lastRxMs = millis();

  char welcome[32];
  snprintf(welcome, sizeof(welcome), "WELCOME:%s", devNonce);
  sendFrame(0, welcome);
  Serial.println(F("[enlace] sesión segura iniciada con la app"));
  sendData();
  if (offCount > 0) { Serial.print(F("[offline] lecturas por reenviar: ")); Serial.println(offCount); }
}

void handleCommand(const char *body, uint32_t seq) {
  char ack[20];
  snprintf(ack, sizeof(ack), "%lu", (unsigned long)seq);

  if (strncmp(body, "PING:", 5) == 0) {
    char pong[40];
    snprintf(pong, sizeof(pong), "PONG:%s", body + 5);
    send(pong);
  } else if (strcmp(body, "VENT:1") == 0 || strcmp(body, "VENT:0") == 0) {
    ventOpen = (body[5] == '1');
    applyOutputs();
    char ackStr[32];
    snprintf(ackStr, sizeof(ackStr), "ACK:%lu", (unsigned long)seq);
    send(ackStr);
    sendData();
  } else if (strncmp(body, "FAN:", 4) == 0) {
    int v = atoi(body + 4);
    if (v < 0 || v > 100) { send("ERR:RANGO"); return; }
    fanPct = v;
    applyOutputs();
    char ackStr[32];
    snprintf(ackStr, sizeof(ackStr), "ACK:%lu", (unsigned long)seq);
    send(ackStr);
    sendData();
  } else if (strcmp(body, "MODE:A") == 0 || strcmp(body, "MODE:M") == 0) {
    autoMode = (body[5] == 'A');
    if (autoMode) {
      processAutoMode();
    }
    char ackStr[32];
    snprintf(ackStr, sizeof(ackStr), "ACK:%lu", (unsigned long)seq);
    send(ackStr);
    sendData();
  } else {
    send("ERR:CMD");
  }
}

void handleLine(char *l) {
  char *p1 = strchr(l, '|');
  char *p2 = strrchr(l, '|');
  if (!p1 || p1 == p2) { rejected++; return; }
  *p1 = '\0'; *p2 = '\0';
  const char *seqStr = l, *body = p1 + 1, *mac = p2 + 1;
  if (strlen(seqStr) == 0 || strlen(seqStr) > 10 || !isHexStr(mac, 16) || !isValidBody(body)) { rejected++; return; }
  for (const char *c = seqStr; *c; c++) if (*c < '0' || *c > '9') { rejected++; return; }
  uint32_t seq = strtoul(seqStr, nullptr, 10);

  if (strncmp(body, "HELLO:", 6) == 0) { handleHello(body, seq, mac); return; }
  if (!session) { rejected++; return; }

  char expected[17];
  computeMac('A', appNonce, devNonce, seq, body, expected);
  if (!macEquals(mac, expected)) { rejected++; Serial.println(F("[enlace] MAC inválido: mensaje descartado")); return; }
  if (seq <= rxSeq) { rejected++; Serial.println(F("[enlace] mensaje repetido descartado")); return; }
  rxSeq = seq;
  lastRxMs = millis();
  if (strncmp(body, "PING:", 5) != 0) { Serial.print(F("[orden] ")); Serial.println(body); }
  handleCommand(body, seq);
}

void readClient() {
  while (client && client.available()) {
    char c = client.read();
    if (c == '\r') continue;
    if (c == '\n') {
      if (!overflow && lineLen > 0) { line[lineLen] = '\0'; handleLine(line); }
      lineLen = 0; overflow = false;
    } else if (lineLen < MAX_LINE) {
      line[lineLen++] = c;
    } else {
      overflow = true;
    }
  }
}

// ---------- Sensores ----------

void readSensors() {
  float t = dht.readTemperature();
  float h = dht.readHumidity();
  dhtOk = !(isnan(t) || isnan(h));
  if (dhtOk) { temperature = (int)lround(t); humidity = (int)lround(h); }
}

void halt(const __FlashStringHelper *msg) {
  Serial.println(msg);
  while (true) delay(1000);
}

// ---------- Arranque ----------

void setup() {
  Serial.begin(115200, SERIAL_8N1, SERIAL_TX_ONLY);
  pinMode(PIN_FAN, FUNCTION_3);
  pinMode(PIN_FAN, OUTPUT);
  analogWriteRange(1023);
  analogWrite(PIN_FAN, 0);
  pinMode(PIN_PIR, INPUT);

  servo.attach(PIN_SERVO, SERVO_PULSO_MIN, SERVO_PULSO_MAX);
  servo.write(SERVO_CLOSED);
  delay(350);
  servo.detach();
  dht.begin();

  // Clave compartida desde secrets.h
  const char *hex = LINK_KEY_HEX;
  size_t n = strlen(hex);
  if (!(n == 32 || n == 64) || !isHexStr(hex, n)) halt(F("LINK_KEY_HEX mal"));
  keyLen = n / 2;
  for (size_t i = 0; i < keyLen; i++) key[i] = (hexVal(hex[2 * i]) << 4) | hexVal(hex[2 * i + 1]);
  if (strlen(AP_PASSWORD) < 8) halt(F("Clave WiFi < 8"));

  WiFi.mode(WIFI_AP);
  WiFi.setOutputPower(14.0); // Reducir potencia RF a 14 dBm para optimizar consumo de corriente
  WiFi.softAP(AP_SSID, AP_PASSWORD, 6, false, 1);
  server.begin();
  server.setNoDelay(true);

  Serial.print(F("Red: ")); Serial.print(AP_SSID); Serial.print(F("  IP: ")); Serial.println(WiFi.softAPIP());
  delay(1500);
  readSensors();
}

void loop() {
  if (!client || !client.connected()) {
    WiFiClient incoming = server.accept();
    if (incoming) {
      if (client) client.stop();
      client = incoming;
      client.setNoDelay(true);
      session = false;
      lineLen = 0; overflow = false;
      lastRxMs = millis();
      Serial.println(F("[enlace] teléfono conectado"));
    }
  }

  readClient();
  unsigned long now = millis();

  if (session && now - lastRxMs > LINK_TIMEOUT_MS) { failSafe(); if (client) client.stop(); }
  if (!session && ventOpen && now - lastRxMs > LINK_TIMEOUT_MS) failSafe();

  bool m = digitalRead(PIN_PIR) == HIGH;
  if (m != movement) {
    movement = m;
    processAutoMode();
    sendData();
  }

  if (now - lastDataMs >= DATA_EVERY_MS) {
    lastDataMs = now;
    readSensors();
    processAutoMode();
    sendData();
  }

  // Sin app: guardar lecturas para reenviarlas después. Con app: reenviar las pendientes.
  if (!session && now - lastOfflineMs >= OFFLINE_EVERY_MS) { lastOfflineMs = now; storeOffline(); }
  if (session && offCount > 0 && now - lastHistMs >= HIST_EVERY_MS) { lastHistMs = now; flushOneOffline(); }

  delay(session ? LOOP_DELAY_ACTIVE_MS : LOOP_DELAY_IDLE_MS);
}
