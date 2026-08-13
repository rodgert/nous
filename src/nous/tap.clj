; SPDX-License-Identifier: EPL-2.0
(ns nous.tap
  "Analysis tap reader — the studio's \"ears\".

  Receives `msg_tap` (0x58) frames pushed by kairos draining its tap bus —
  named analysis/probe values (spectral peaks, envelope, level, tuner cents)
  measured on the audio thread — and holds the latest values for reading from
  the REPL, MCP, or an audio assertion.

  ## Reading

    (tap/taps)                      ; => {:spectral/peak-0-freq 0.0109 …}
    (tap/tap :spectral/peak-0-freq) ; => 0.0109  (normalized freq, DC→Nyquist)
    (tap/tap-epoch)                 ; => schema generation of the latest frame

  ## Why an ephemeral atom, not the ctrl-tree

  Tap values update at ~30 Hz and every ctrl-tree write hits the SQLite txlog,
  so routing taps through it would bloat the replay log (authority-doc rule 6 —
  high-churn observation, not authored state). Values live in a plain atom here.
  Mounting taps as `[:tap …]` ctrl-tree paths (so cables and BEAM matter can read
  them) awaits the ephemeral-mount decision — see
  `nomos-studio/plans/tap-ipc-bridge-design.md` open decision #4."
  (:require [clojure.edn :as edn]
            [nous.rt     :as rt]))

;; ---------------------------------------------------------------------------
;; State — the latest tap frame
;; ---------------------------------------------------------------------------

(defonce ^:private tap-state (atom {:epoch 0 :taps {}}))

;; ---------------------------------------------------------------------------
;; Read API
;; ---------------------------------------------------------------------------

(defn taps
  "Return the latest tap map `{tap-name value}`. Empty until the first frame."
  []
  (:taps @tap-state))

(defn tap
  "Return the latest value for tap `k` (a keyword, e.g. :spectral/peak-0-freq),
  or nil if no such tap is present in the latest frame."
  [k]
  (get (:taps @tap-state) k))

(defn tap-epoch
  "Return the tap-schema epoch of the most recent frame (increments on kairos
  graph reload)."
  []
  (:epoch @tap-state))

;; ---------------------------------------------------------------------------
;; Frame handling
;; ---------------------------------------------------------------------------

(defn -handle-tap-frame!
  "Parse a `msg_tap` EDN payload string `{:epoch N :taps {…}}` and store it as
  the latest frame. Public for testing / out-of-band injection."
  [^String edn-str]
  (let [{:keys [epoch taps] :or {epoch 0 taps {}}} (edn/read-string edn-str)]
    (reset! tap-state {:epoch (long epoch) :taps taps})
    nil))

;; Register the msg_tap (0x58) push handler on load. Fires on the rt reader
;; thread each time kairos pushes a tap frame.
(def ^:private _tap-handler
  (rt/register-push-handler!
   0x58
   (fn [^bytes payload]
     (-handle-tap-frame! (String. payload "UTF-8")))))
