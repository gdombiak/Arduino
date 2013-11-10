/* Furby :) */

#define cam_stop_event 1
#define cam_home_event 2
#define timeout_event 3

#define no_op 0
#define motor_stop_op 1
#define motor_quick_stop_op 2
#define motor_forward_op 3
#define motor_backward_op 4
#define cam_goto_op 5
#define lip_sync_enable_op 6

const boolean quiet = true;

const int photo_int = 0; // pin 2
const int photo_pin = 2;
const int home_sw_int = 1; // pin 3
const int home_sw_pin = 3;
const int audio_sample_pin = A0;
const int motor_a_pin = 4;
const int motor_b_pin = 5;
const int pwm_pin = 6;
const int tongue_sw_pin = 7;
const int belly_sw_pin = 8;
const int cam_max = 200;
const int cam_half_max = 100;
const long end_of_time = 2147483647;

boolean motor_enabled = true;

volatile int cam_count = 0;
volatile int cam_position = 0;
volatile boolean cam_home_reached = false;
volatile boolean cam_stop_reached = false;
boolean timeout_reached = false;
char cam_direction = 0;
byte cam_stop_op = no_op;
byte cam_stop_op_param = 0;
byte cam_home_op = no_op;
byte cam_home_op_param = 0;
long timeout = end_of_time;
byte timeout_op = no_op;
byte timeout_op_param = 0;

boolean lip_sync_enable = true;
byte last_sample = 0;
int recent_samples[8];
int recent_samples_ptr = 0;

boolean belly_sw_state = false;
boolean belly_sw_prev_state = false;
boolean tongue_sw_state = false;
boolean tongue_sw_prev_state = false;
boolean light_sw_state = false;
boolean light_sw_prev_state = false;

char input[24];
int input_ptr = 0;
int input_read = 0;

void setup() {
  pinMode(photo_pin, INPUT_PULLUP);
  digitalWrite(photo_pin, HIGH);
  pinMode(motor_a_pin, OUTPUT);
  digitalWrite(motor_a_pin, LOW);
  pinMode(motor_b_pin, OUTPUT);
  digitalWrite(motor_b_pin, LOW);
  pinMode(pwm_pin, OUTPUT);
  digitalWrite(pwm_pin, LOW);
  pinMode(home_sw_pin, INPUT_PULLUP);
  digitalWrite(home_sw_pin, HIGH);
  pinMode(tongue_sw_pin, INPUT_PULLUP);
  digitalWrite(tongue_sw_pin, HIGH);
  pinMode(belly_sw_pin, INPUT_PULLUP);
  digitalWrite(belly_sw_pin, HIGH);
  analogReference(INTERNAL); // set vref = 1.1v
  for (int i = 0; i < 8; i++) {
    recent_samples[i] = 0;
  }
  Serial.begin(115200);
  attachInterrupt(photo_int, count, FALLING);
  attachInterrupt(home_sw_int, countHome, FALLING);
  
  setDirectionAndSpeed(1, 10);
  delay(250);
}

void count() {
  cam_count--;
  if (cam_count == 0) {
    cam_stop_reached = true;
  }
  cam_position = (cam_direction + cam_position) % cam_max;
}

void countHome() {
  cam_position = 0 - cam_direction * 2;
  cam_home_reached = true;
}

void setDirectionAndSpeed(char dir, byte spd) {
  char d = min(max(dir, -1), 1);
  byte s = min(max(spd, 0), 10);
  if (s == 0) d = 0;
  else if (d == 0) s = 0;
  cam_direction = d;
  if (!quiet) {
    Serial.write("setDirectionAndSpeed(dir=");
    Serial.print(d);
    Serial.write(",spd=");
    Serial.print(s);
    Serial.write(")");
    Serial.println();
  }
  digitalWrite(motor_a_pin, cam_direction < 0);
  digitalWrite(motor_b_pin, cam_direction > 0);
  if (cam_direction == 0 || s == 0 || !motor_enabled) {
    digitalWrite(pwm_pin, LOW);
  } else {
    analogWrite(pwm_pin, map(spd + 12, 0, 22, 0, 255));
  }
}

void camMove(int p) {
  int c = cam_position + cam_direction * 5;
  int delta = (c + cam_max - p) % cam_max;
  if (delta > cam_half_max) {
    if (cam_direction == 1) {
      processOp(motor_quick_stop_op, 10);
    } else if (cam_direction == 0) {
      cam_count = abs(p);
      setEventOp(cam_stop_event, motor_quick_stop_op, 10, 0);
      processOp(motor_backward_op, 10);
    } else {
      cam_count = abs(p);
      setEventOp(cam_stop_event, motor_quick_stop_op, 10, 0);
    }
  } else {
    if (cam_direction == -1) {
      processOp(motor_quick_stop_op, 10);
    } else if (cam_direction == 0) {
      cam_count = abs(p);
      setEventOp(cam_stop_event, motor_quick_stop_op, 10, 0);
      processOp(motor_forward_op, 10);
    } else {
      cam_count = abs(p);
      setEventOp(cam_stop_event, motor_quick_stop_op, 10, 0);
    }
  }
}

void camGoto(byte pos) {
  if (pos == 0) { // home
    if (digitalRead(home_sw_pin) == LOW || cam_position == 0) {
        // already there
        if (cam_direction != 0) {
          processOp(motor_quick_stop_op, 10);
        }
        return;
    }
    if (!quiet) {
      Serial.write("goto: home    current=");
      Serial.println(cam_position);
    }
    setEventOp(cam_home_event, motor_quick_stop_op, 10, 0);
    int c = cam_position + cam_direction * 5;
    if (c < cam_half_max) {
      if (cam_direction >= 0) {
        processOp(motor_backward_op, 7);
      }
    } else {
      if (cam_direction <= 0) {
        processOp(motor_forward_op, 7);
      }
    }
    return;
  }
  int desired_pos = map(pos % 100, 0, 100, 0, cam_max);
  if (!quiet) {
    Serial.write("goto: ");
    Serial.print(pos);
    Serial.write("    (");
    Serial.print(desired_pos);
    Serial.write(")");
    Serial.println();
  }
  camMove(desired_pos - cam_position);
}

void setEventOp(byte event, byte op, byte param, int ms) {
  switch (event) {
    case cam_stop_event:
      noInterrupts();
      cam_home_op = no_op;
      cam_stop_op = op;
      cam_stop_op_param = param;
      interrupts();
      break;
    case cam_home_event:
      noInterrupts();
      cam_stop_op = no_op;
      cam_home_op = op;
      cam_home_op_param = param;
      interrupts();
      break;
    case timeout_event:
      timeout = millis() + ms;
      timeout_op = op;
      timeout_op_param = param;
      break;
  }
}

void processOp(byte op, byte param) {
  switch (op) {
    case motor_stop_op: // motor stop
      if (cam_direction != 0) {
        if (!quiet) {
          Serial.write("stop!");
          Serial.println();
        }
        setDirectionAndSpeed(0, 0);
      }
      break;
    case motor_quick_stop_op: // motor quick stop
      if (cam_direction != 0) {
        if (!quiet) {
          Serial.write("quick stop! @ ");
          Serial.println(param);
        }
        setEventOp(timeout_event, motor_stop_op, 0, 25);
        setDirectionAndSpeed(0 - cam_direction, param);
      }
      break;
    case motor_forward_op: // motor forward
      if (!quiet) {
        Serial.write("forward @ ");
        Serial.println(param);
      }
      setDirectionAndSpeed(1, param);
      break;
    case motor_backward_op: // motor backward
      if (!quiet) {
        Serial.write("backward @ ");
        Serial.println(param);
      }
      setDirectionAndSpeed(-1, param);
      break;
    case cam_goto_op: // cam goto
      camGoto(param);
      break;
    case lip_sync_enable_op:
      lip_sync_enable = param != 0;
      break;
  }
}

void sendSwitchState(char* name, boolean state) { // sw(belly)=1
    Serial.write("sw(");
    Serial.write(name);
    Serial.write(")=");
    if (state) {
      Serial.write("1");
    } else {
      Serial.write("0");
    }
    Serial.println();
}

int parseIntFromInput() {
  boolean neg = false;
  if (input[input_read] == '-') {
    if (input_read >= 23) return 0;
    char ch = input[input_read++];
    if (ch < '0' || ch > '9') {
      return 0; // only found a "-", not a number
    }
    neg = true;
    input_read++;
  }
  int j = min(input_read + 4, 24);
  int value = 0;
  while (input_read < j) {
    char ch = input[input_read];
    if (ch >= '0' && ch <= '9') {
      value = value * 10 + (ch - '0');
      input_read++;
    } else if (neg) {
      return 0 - value;
    } else {
      return value;
    }
  }
}

void handleInput() { // op(code,param)
//  if (!quiet) {
    Serial.write("Handling input: ");
    for (int i = 0; i < input_ptr; i++) {
      Serial.write(input[i]); 
    }
//  }
  if (input[0] == 'o' && input[1] == 'p' && input[2] == '(') {
    input_read = 3;
    byte op = parseIntFromInput();
    if (input_read > 22 || input[input_read] != ',') {
      return; // malformed
    }
    input_read++;
    byte param = parseIntFromInput();
    if (input_read > 22 || input[input_read] != ')' || input[input_read + 1] != '\n') {
      return; // malformed
    }
//    if (!quiet) {
      Serial.write("Handling input: processOp(");
      Serial.print(op);
      Serial.write(",");
      Serial.print(param);
      Serial.write(")");
      Serial.println();
//    }
    processOp(op, param);
  }
}

void loop() {

  // process events
  if (cam_stop_reached) {
    cam_stop_reached = false;
    if (cam_stop_op != no_op) {
      if (!quiet) {
        Serial.write("Handling event: stop_reached_event - ");
        Serial.println(cam_stop_op);
      }
      byte op = cam_stop_op;
      cam_stop_op = no_op;
      processOp(op, cam_stop_op_param);
      return; // run the loop again
    }
  }
  if (cam_home_reached) {
    cam_home_reached = false;
    if (cam_home_op != no_op) {
      if (!quiet) {
        Serial.write("Handling event: home_reached_event - ");
        Serial.println(cam_home_op);
      }
      byte op = cam_home_op;
      cam_home_op = no_op;
      processOp(op, cam_home_op_param);
      return; // run the loop again
    }
  }
  
  long now = millis();
  
//  if (motor_enabled && now > 10000) {
//    setDirectionAndSpeed(0,0);
//    Serial.write("motor disabled!");
//    Serial.println();
//    motor_enabled = false;
//    return;
//  }
  
  if (timeout_op != no_op && timeout <= now) {
    if (!quiet) {
      Serial.write("Handling event: timeout_event - ");
      Serial.println(timeout_op);
    }
    byte op = timeout_op;
    timeout_op = no_op;
    processOp(op, timeout_op_param);
    return;
  }

  byte current_sample = now >> 6; // update every 4ms - 250/s
  if (lip_sync_enable && last_sample != current_sample) {
    last_sample = current_sample;
    int sample = analogRead(audio_sample_pin);
    recent_samples[recent_samples_ptr] = sample;
    long sum = 0;
    int ptr = recent_samples_ptr;
    for (int i = 8; i > 0; i--) {
      sum += recent_samples[ptr] * i;
      ptr--;
      if (ptr < 0) {
        ptr += 8;
      }
    }
    recent_samples_ptr = (recent_samples_ptr + 1) & 7;
    if (sum < 200) {
      camGoto(0);
    } else {
      camGoto(15);
    }
    if (light_sw_state) {
      light_sw_state = analogRead(A1) > 150;
    } else {
      light_sw_state = analogRead(A1) > 250;
    }
    return;
  }

  while (Serial.available() > 0) {
    char ch = Serial.read();
    if (input_ptr < 24) {
      input[input_ptr] = ch;
      input_ptr++;
    }
    if (ch == '\n') {
      if (input_ptr < 24) {
        handleInput();
      }
      input_ptr = 0;
    }
  }

  belly_sw_state = digitalRead(belly_sw_pin) == LOW;
  if (belly_sw_state != belly_sw_prev_state) {
    belly_sw_prev_state = belly_sw_state;
    sendSwitchState("belly", belly_sw_state);
  }

  tongue_sw_state = digitalRead(tongue_sw_pin) == LOW;
  if (tongue_sw_state != tongue_sw_prev_state) {
    tongue_sw_prev_state = tongue_sw_state;
    sendSwitchState("tongue", tongue_sw_state);
  }

  if (light_sw_state != light_sw_prev_state) {
    light_sw_prev_state = light_sw_state;
    sendSwitchState("light", light_sw_state);
  }
}
