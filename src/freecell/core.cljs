(ns freecell.core)

(def deck
  "An unshuffled 52-card deck. Each card is a map with :rank and :suit."
  (for [rank (range 1 14) suit [:clubs :diamonds :hearts :spades]] {:rank rank :suit suit}))

(defn get-color
  "Return the color of the card (:black or :red)."
  [card]
  (case (:suit card)
    (:clubs :spades) :black
    (:diamonds :hearts) :red))

(defn stackable?
  "Do the over and under cards form a valid stack?

  Note that over and under refer to the \"z-axis\". This means, for example,
  that cards that are at the bottom of a stack *vertically* are actually at the
  top of the stack in our implementation."
  [over under]
  (and
    (= (:rank over) (dec (:rank under)))
    (not= (get-color over) (get-color under))))

(def empty-game
  "An empty game. A game is a map from location types (cells, foundations, and
  stacks) to vectors of card lists.

  A [location-type location-index] pair form a location, or loc."
  {
   :cell (vec (repeat 4 '()))
   :foundation (vec (repeat 4 '()))
   :stack (vec (repeat 8 '()))})

(defn get-foundation-for-suit
  "Return the foundation assigned to the given suit."
  [game suit]
  [:foundation (or
                 ; if the suit is already in a foundation, use that
                 (first (filter (comp
                                  (partial = suit)
                                  :suit
                                  first
                                  #(get-in game [:foundation %])) (range 4)))
                 ; otherwise, use first empty
                 (first (filter (comp
                                  empty?
                                  #(get-in game [:foundation %])) (range 4))))])

(defn deal
  "Return a new game with the cards randomly shuffled and dealt."
  []
  (let [cards (shuffle deck)
        game empty-game]
    (loop [g game, i 0, cs cards]
      (if (empty? cs)
        g
        (recur
          (update-in g [:stack i] #(conj % (first cs)))
          (mod (inc i) 8)
          (rest cs))))))

(defn get-free-cells
  "Return a seq of the free (empty) cells in the game."
  [game]
  (filter #(empty? (get-in game %)) (map #(-> [:cell %]) (range 4))))

(defn get-free-stacks
  "Return a seq of the free (empty) stacks in the game."
  [game]
  (filter #(empty? (get-in game %)) (map #(-> [:stack %]) (range 8))))

(defn get-free-locs
  "Return a seq of the free (empty) cells and stacks in the game."
  [game]
  (concat (get-free-cells game) (get-free-stacks game)))

(defn get-free-cell
  "Return a free cell, or nil if there are none."
  [game]
  (first (get-free-cells game)))

(defn can-accept-card?
  "Based on the destination type and the cards it contains, can src-card be added?"
  [src-card dst-type dst-cards]
  (let [dst-top (peek dst-cards)]
    (case dst-type
      :cell (empty? dst-cards)
      :foundation
        (or
          (and (empty? dst-cards) (= (:rank src-card) 1))
          (and
            (= (:suit src-card) (:suit dst-top))
            (= (dec (:rank src-card)) (:rank dst-top))))
      :stack
        (or (empty? dst-cards) (stackable? src-card dst-top)))))

(defn can-move?
  "Is it legal to move a single card from src to dst?"
  [game [src-type src-index :as src] [dst-type dst-index :as dst]]
  (let [src-cards (get-in game src)
        dst-cards (get-in game dst)]
    (cond
      (empty? src-cards) false
      (= src-type :foundation) false
      :else (can-accept-card? (first src-cards) dst-type dst-cards))))

(defn move
  "Return a new game with n cards moved from src to dst."
  [game src dst n]
  (let [src-cards (get-in game src)
        dst-cards (get-in game dst)]
    (-> game
        (assoc-in src (apply list (drop n src-cards)))
        (assoc-in dst (apply list (concat (take n src-cards) dst-cards))))))

(defn get-num-movable
  "How many cards can legally be moved in one shot from src to dst?"
  [game [src-type src-index :as src] [dst-type dst-index :as dst]]
  (loop [g game accum 0]
    (let [src-cards (get-in g src)
          dst-cards (get-in g dst)
          src-card (first src-cards)
          next-card (second src-cards)
          free-locs (get-free-locs g)]
      (cond
        (can-move? g src dst) (inc accum)
        (and
          (= src-type dst-type :stack)
          (some? next-card)
          (stackable? src-card next-card)
          (seq free-locs)) (recur (move g src (first free-locs) 1) (inc accum))
        :else 0))))

(defn get-min-rank
  "Return the minimum rank of cards of the given color that are still in play.

  If no cards of the given color are still in play, return 14."
  [game color]
  (if-some [color-cards (seq (filter
                               (comp (partial = color) get-color)
                               (apply concat (concat (:cell game) (:stack game)))))]
    (apply min (map :rank color-cards))
    14))

(defn auto-move
  "Automatically move all eligible cards to their foundations."
  [game]
  (let [all-locs (concat (map #(-> [:cell %]) (range 4)) (map #(-> [:stack %]) (range 8)))]
    (loop [g game ls all-locs]
      (if-some [loc (first ls)]
        (if-some [top-card (first (get-in g loc))]
          (let [other-color (case (get-color top-card) :red :black :red)
                other-color-min-rank (get-min-rank g other-color)
                cutoff (max other-color-min-rank 2)  ; 2s can always be auto-moved
                dst (get-foundation-for-suit g (:suit top-card))]
            (if (and (can-accept-card? top-card :foundation (get-in g dst)) (<= (:rank top-card) cutoff))
              ;; if we're making a move, we restart with all-locs
              (recur (move g loc dst 1) all-locs)
              (recur g (rest ls))))
          (recur g (rest ls)))
        g))))

(defn won?
  "Has the game been won?"
  [game]
  (every? (comp (partial = 13) :rank first) (:foundation game)))
