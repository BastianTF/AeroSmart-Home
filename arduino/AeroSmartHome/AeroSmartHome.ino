/*
 * AeroSmart Home - Wemos D1 mini (ESP8266)
 *
 * Sensores: DHT11 (temperatura y humedad) y PIR HC-SR501 (presencia).
 * Actuadores: servo SG90 (compuerta) y turbina (transistor o MOSFET en el pin RX).
 * Pantalla: LCD 16x2 en modo 4 bits.
 *
 * Comunicación con la app: la placa crea su propia red Wi-Fi WPA2 (AP_SSID) y escucha en el puerto 3333.
 * Por ese canal serial viajan líneas de texto   SEQ|CUERPO|MAC
 *   MAC = HMAC-SHA256(clave, DIR|SESION|SEQ|CUERPO), primeros 8 bytes en hexadecimal.
 *   DIR = 'A' (app -> placa) o 'D' (placa -> app). SESION = nonceApp + noncePlaca (nuevos en cada conexión).
 *   SEQ siempre sube: un mensaje repetido o con MAC incorrecto se descarta.
 * Es el mismo protocolo de app/src/main/java/.../link/SecureChannel.kt.
 *
 * Órdenes que recibe:  HELLO:<nonce>  PING:<ms>  VENT:0|1  FAN:0-100  MODE:A|M
 * Mensajes que envía:  WELCOME:<nonce>  PONG:<ms>  ACK:<seq>  DATA:temp,hum,pir,compuerta,turbina  ERR:<código>
 *
 * Seguridad ante fallas: si la app deja de hablar por 15 s, la placa cierra la compuerta y apaga la turbina.
 *
 * Librerías (Arduino IDE > Herramientas > Administrar bibliotecas):
 *   - "DHT sensor library" de Adafruit (y "Adafruit Unified Sensor")
 *   - LiquidCrystal y Servo vienen con el core ESP8266. HMAC-SHA256 usa BearSSL, que también viene incluido.
 */
#include <ESP8266WiFi.h>
#include <Servo.h>
#include <DHT.h>
#include <LiquidCrystal.h>
#include <bearssl/bearssl.h>
#include "secrets.h"

// ---------- Pines (cámbialos aquí si tu armado es distinto) ----------
const uint8_t PIN_DHT   = D5;
const uint8_t PIN_PIR   = D6;
const uint8_t PIN_SERVO = D7;
const uint8_t PIN_FAN   = 3;    // RX (GPIO3): libre porque el monitor serie se usa solo para enviar
// LCD: RS, E, D4, D5, D6, D7
LiquidCrystal lcd(D1, D2, D3, D4, D0, D8);

// ---------- Ajustes ----------
const uint16_t PORT = 3333;
const int SERVO_CLOSED = 0;
const int SERVO_OPEN = 90;
const unsigned long DATA_EVERY_MS = 2000;
const unsigned long LINK_TIMEOUT_MS = 15000;
const unsigned long LCD_EVERY_MS = 1000;
const size_t MAX_LINE = 120;

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
unsigned long lastRxMs = 0, lastDataMs = 0, lastLcdMs = 0;
uint32_t rejected = 0;

bool ventOpen = false;
bool movement = false;
bool autoMode = true;
uint8_t fanPct = 60;
int temperature = 0, humidity = 0;
bool dhtOk = false;

char line[MAX_LINE + 1];
size_t lineLen = 0;
bool overflow = false;

// ---------- Utilidades ----------

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

// CUERPO válido: MAYÚSCULAS ':' y hasta 60 caracteres [A-Za-z0-9,.-]
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

// MAC = HMAC-SHA256(clave, dir|s1 s2|seq|cuerpo) -> 16 caracteres hex
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

// Comparación en tiempo constante (no revela cuántos caracteres coinciden)
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

// ---------- Actuadores ----------

void applyOutputs() {
  servo.write(ventOpen ? SERVO_OPEN : SERVO_CLOSED);
  // La turbina solo gira con la compuerta abierta
  analogWrite(PIN_FAN, ventOpen ? map(fanPct, 0, 100, 0, 1023) : 0);
}

void sendData() {
  if (!dhtOk) { send("ERR:DHT"); return; }
  char body[48];
  snprintf(body, sizeof(body), "DATA:%d,%d,%d,%d,%d", temperature, humidity, movement ? 1 : 0, ventOpen ? 1 : 0, ventOpen ? fanPct : 0);
  send(body);
}

void failSafe() {
  // Sin comunicación: estado seguro
  ventOpen = false;
  applyOutputs();
  session = false;
  appNonce[0] = devNonce[0] = '\0';
  Serial.println(F("[enlace] sin datos de la app: compuerta cerrada"));
}

// ---------- Protocolo ----------

void handleHello(const char *body, uint32_t seq, const char *mac) {
  const char *nonce = body + 6;  // después de "HELLO:"
  if (seq != 0 || !isHexStr(nonce, 16)) { rejected++; return; }
  char expected[17];
  computeMac('A', nonce, "", 0, body, expected);
  if (!macEquals(mac, expected)) { rejected++; Serial.println(F("[enlace] HELLO con clave incorrecta")); return; }

  strncpy(appNonce, nonce, 16); appNonce[16] = '\0';
  uint8_t rnd[8];
  ESP.random(rnd, sizeof(rnd));  // generador aleatorio por hardware del ESP8266
  for (int i = 0; i < 8; i++) sprintf(devNonce + 2 * i, "%02x", rnd[i]);
  devNonce[16] = '\0';
  txSeq = 0; rxSeq = 0;
  session = true;
  lastRxMs = millis();

  char welcome[32];
  snprintf(welcome, sizeof(welcome), "WELCOME:%s", devNonce);
  sendFrame(0, welcome);
  Serial.println(F("[enlace] sesión segura iniciada"));
  sendData();
}

void handleCommand(const char *body, uint32_t seq) {
  char ack[20];
  snprintf(ack, sizeof(ack), "ACK:%lu", (unsigned long)seq);

  if (strncmp(body, "PING:", 5) == 0) {
    char pong[40];
    snprintf(pong, sizeof(pong), "PONG:%s", body + 5);
    send(pong);
  } else if (strcmp(body, "VENT:1") == 0 || strcmp(body, "VENT:0") == 0) {
    ventOpen = body[5] == '1';
    applyOutputs();
    send(ack);
    sendData();
  } else if (strncmp(body, "FAN:", 4) == 0) {
    int v = atoi(body + 4);
    if (v < 0 || v > 100) { send("ERR:RANGO"); return; }
    fanPct = v;
    applyOutputs();
    send(ack);
    sendData();
  } else if (strcmp(body, "MODE:A") == 0 || strcmp(body, "MODE:M") == 0) {
    autoMode = body[5] == 'A';
    send(ack);
  } else {
    send("ERR:CMD");
  }
}

void handleLine(char *l) {
  // Separar SEQ|CUERPO|MAC
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
      overflow = true;  // línea demasiado larga: se descarta entera
    }
  }
}

// ---------- Sensores y pantalla ----------

void readSensors() {
  float t = dht.readTemperature();
  float h = dht.readHumidity();
  dhtOk = !(isnan(t) || isnan(h));
  if (dhtOk) { temperature = (int)lround(t); humidity = (int)lround(h); }
}

void updateLcd() {
  char r0[17], r1[17];
  if (dhtOk) snprintf(r0, sizeof(r0), "T%2dC H%2d%% Mov:%s", temperature, humidity, movement ? "SI" : "NO");
  else snprintf(r0, sizeof(r0), "DHT11 sin datos ");
  snprintf(r1, sizeof(r1), "%-7s %3d%% %s", ventOpen ? "ABIERTA" : "CERRADA", ventOpen ? fanPct : 0, session ? "APP" : "---");
  lcd.setCursor(0, 0); lcd.print(r0); for (size_t i = strlen(r0); i < 16; i++) lcd.print(' ');
  lcd.setCursor(0, 1); lcd.print(r1); for (size_t i = strlen(r1); i < 16; i++) lcd.print(' ');
}

void halt(const __FlashStringHelper *msg) {
  lcd.clear(); lcd.print(F("Error config.")); lcd.setCursor(0, 1); lcd.print(msg);
  Serial.println(msg);
  while (true) delay(1000);
}

// ---------- Arranque ----------

void setup() {
  Serial.begin(115200, SERIAL_8N1, SERIAL_TX_ONLY);  // deja el pin RX libre para la turbina
  pinMode(PIN_FAN, FUNCTION_3);
  pinMode(PIN_FAN, OUTPUT);
  analogWriteRange(1023);
  analogWrite(PIN_FAN, 0);
  pinMode(PIN_PIR, INPUT);

  lcd.begin(16, 2);
  lcd.print(F("AeroSmart Home"));
  servo.attach(PIN_SERVO);
  servo.write(SERVO_CLOSED);
  dht.begin();

  // Clave compartida desde secrets.h
  const char *hex = LINK_KEY_HEX;
  size_t n = strlen(hex);
  if (!(n == 32 || n == 64) || !isHexStr(hex, n)) halt(F("LINK_KEY_HEX mal"));
  keyLen = n / 2;
  for (size_t i = 0; i < keyLen; i++) key[i] = (hexVal(hex[2 * i]) << 4) | hexVal(hex[2 * i + 1]);
  if (strlen(AP_PASSWORD) < 8) halt(F("Clave WiFi < 8"));

  WiFi.mode(WIFI_AP);
  // canal 6, red visible, máximo 1 teléfono conectado
  WiFi.softAP(AP_SSID, AP_PASSWORD, 6, false, 1);
  server.begin();
  server.setNoDelay(true);

  Serial.print(F("Red: ")); Serial.print(AP_SSID); Serial.print(F("  IP: ")); Serial.println(WiFi.softAPIP());
  lcd.setCursor(0, 1); lcd.print(WiFi.softAPIP());
  delay(1500);
  readSensors();
}

void loop() {
  // Nueva conexión: reemplaza a la anterior y obliga a un saludo nuevo
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

  readClient();
  unsigned long now = millis();

  if (session && now - lastRxMs > LINK_TIMEOUT_MS) { failSafe(); if (client) client.stop(); }
  if (!session && ventOpen && now - lastRxMs > LINK_TIMEOUT_MS) failSafe();

  bool m = digitalRead(PIN_PIR) == HIGH;
  if (m != movement) { movement = m; sendData(); }  // la presencia se informa al instante

  if (now - lastDataMs >= DATA_EVERY_MS) {
    lastDataMs = now;
    readSensors();
    sendData();
  }
  if (now - lastLcdMs >= LCD_EVERY_MS) { lastLcdMs = now; updateLcd(); }
  delay(5);
}
