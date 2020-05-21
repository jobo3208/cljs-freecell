(ns ^:figwheel-hooks freecell.main
  (:require
   [freecell.core :as fc]
   [goog.dom :as gdom]
   [react :as react]
   [react-dom :as react-dom]
   [sablono.core :as sab :include-macros true]
   [cljsjs.seedrandom :as seedrandom]))

(defn card-to-str
  "Return a string representation of a card."
  [card]
  (str
    (case (:rank card)
      1 "A"
      2 "2"
      3 "3"
      4 "4"
      5 "5"
      6 "6"
      7 "7"
      8 "8"
      9 "9"
      10 "10"
      11 "J"
      12 "Q"
      13 "K")
    (case (:suit card)
      :clubs \u2663
      :diamonds \u2666
      :hearts \u2665
      :spades \u2660)))

(defn start-state []
  {:illegal-move-src nil
   :src nil
   :menu-showing false
   :game (fc/deal)})

(defonce app-state (atom (start-state)))

(defn new-game
  ([] (reset! app-state (start-state)))
  ([seed]
   (.seedrandom js/Math seed)
   (new-game)))

(defn clear-move []
  (swap! app-state #(assoc % :src nil)))

(defn toggle-menu []
  (swap! app-state #(update % :menu-showing not)))

(defn valid-source? [game [loc-type loc-index :as loc]]
  (and (#{:cell :stack} loc-type) (seq (get-in game loc))))

(defn handle-src-click [{:keys [game] :as state} src]
  (if (valid-source? game src)
    (assoc state :illegal-move-src nil :src src)
    state))

(defn handle-dst-click [{:keys [game src] :as state} dst]
  (if (not= src dst)
    (let [n (fc/get-num-movable game src dst)]
      (if (pos? n)
        (assoc state
               :src nil
               :game (-> game
                         (fc/move src dst n)
                         (fc/auto-move)))
        (assoc state :illegal-move-src src :src nil)))
    (assoc state :src nil)))

(defn handle-click [e loc]
  (.stopPropagation e)
  (swap! app-state (fn [{:keys [src] :as state}]
                     (if src
                       (handle-dst-click state loc)
                       (handle-src-click state loc)))))

(defn card-html [card]
  [:div {:class [:card (fc/get-color card)] :key (card-to-str card)} (card-to-str card)])

(defn loc-html [{:keys [illegal-move-src src game]} [loc-type loc-index :as loc]]
  [:div {:class (cond-> [loc-type]
                  (= loc src) (conj :active)
                  (= loc illegal-move-src) (conj :illegal))
         :key loc-index
         :on-click #(handle-click % loc)}
   (let [cards (map card-html (get-in game loc))]
     (if (= loc-type :stack) (reverse cards) (first cards)))])

(defn game-html [state]
  [:div.game {:on-click clear-move}
   (map #(loc-html state [:cell %]) (range 4))
   (map #(loc-html state [:foundation %]) (range 4))
   [:div.separator]
   (map #(loc-html state [:stack %]) (range 8))])

(defn modal-html [state]
  (let [won (fc/won? (:game state))
        menu (:menu-showing state)]
    [:div
     [:div.glass {:on-key-up (fn [e] (when (= (.-keyCode e) 192) (toggle-menu)))
                  :style {:z-index (if (or won menu) 1 0)}
                  :tab-index 0}]
     (when won
       [:div.dialog
        [:h2 "You win!"]
        [:p "Play again?"]
        [:button {:on-click #(new-game)} "Yes"]])
     (when menu
       [:div.dialog
        [:h2 "MENU"]
        [:input#seed {:type :text
                      :placeholder "Optional random seed"}]
        [:button {:on-click #(let [seed (.-value (gdom/getElement "seed"))]
                               (if (seq seed) (new-game seed) (new-game)))} "New Game"]])]))

(defn app-html [state]
  (sab/html [:div
             (modal-html state)
             (game-html state)]))

(defn get-app-element []
  (gdom/getElement "app"))

(defn mount [el]
  (js/ReactDOM.render (app-html @app-state) el)
  (.focus (gdom/getElementByClass "glass")))

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (mount el)))

(mount-app-element)

(add-watch app-state :renderer (fn [_ _ _ _]
                                  (mount-app-element)))

(defn ^:after-load on-reload []
  (mount-app-element))
