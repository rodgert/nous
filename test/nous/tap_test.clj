; SPDX-License-Identifier: EPL-2.0
(ns nous.tap-test
  "Tests for nous.tap — the analysis-tap reader (msg_tap → latest-frame atom)."
  (:require [clojure.test :refer [deftest is testing]]
            [nous.tap     :as tap]))

(deftest handle-tap-frame-stores-latest
  (testing "-handle-tap-frame! parses {:epoch :taps} and exposes it via the read API"
    (tap/-handle-tap-frame!
     "{:epoch 3 :taps {:spectral/peak-0-freq 0.0109 :spectral/peak-0-amp 1.0 :signal/level 0.42}}")
    (is (= 3 (tap/tap-epoch)))
    (is (= 0.0109 (tap/tap :spectral/peak-0-freq)))
    (is (= 1.0    (tap/tap :spectral/peak-0-amp)))
    (is (= 0.42   (tap/tap :signal/level)))
    (is (= {:spectral/peak-0-freq 0.0109 :spectral/peak-0-amp 1.0 :signal/level 0.42}
           (tap/taps)))))

(deftest latest-frame-replaces-previous
  (testing "a new frame replaces the previous one (latest-wins, not merge)"
    (tap/-handle-tap-frame! "{:epoch 1 :taps {:a 1.0 :b 2.0}}")
    (tap/-handle-tap-frame! "{:epoch 2 :taps {:a 9.0}}")
    (is (= 2 (tap/tap-epoch)))
    (is (= 9.0 (tap/tap :a)))
    (is (nil? (tap/tap :b)) "stale tap from the previous frame is gone")))

(deftest unknown-tap-is-nil
  (testing "reading a tap not present in the latest frame returns nil"
    (tap/-handle-tap-frame! "{:epoch 0 :taps {:only-this 1.0}}")
    (is (nil? (tap/tap :not-there)))))

(deftest empty-taps-frame
  (testing "a frame with no taps (no tap-bus plugin) is handled"
    (tap/-handle-tap-frame! "{:epoch 5 :taps {}}")
    (is (= 5 (tap/tap-epoch)))
    (is (= {} (tap/taps)))
    (is (nil? (tap/tap :anything)))))
