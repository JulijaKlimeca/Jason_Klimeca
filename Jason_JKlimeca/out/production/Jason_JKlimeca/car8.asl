// Agent car8 in project jason_klimeca

post(post1).
post(post2).
charge_station(charger1).
charge_station(charger2).

/* Initial beliefs */

at(P) :- pos(P,X,Y) & pos(car,X,Y).

battery_load(100).

pos(charger1, X, Y).
pos(charger2, X, Y).

/* Initial goals */

!check(cells). // Lai parbauditu katru rezgi
!monitor_battery.  // Lai sekotu akumulatora limenim

/* Plans */

+!check(cells) : not package(car)
   <- .print("Car Agent is going to check next cell");
      next(cell);
      !check(cells).

+at(P) : post(P)
   <- next(cell);
      !check(cells).

+!check_battery_status //parbauda akumulatora limeni
   <- ?battery_load(L);
      if (L <= 20) { //ja akumulatora limenis mazak par 20
          !move_to_charger; //aiziet uz uzlades staciju
          !charge_battery //uzladeties
      }.

+!move_to_the_next_cell(X, Y)
   <- ?pos(car, X1, Y1);
      !check_battery_and_charge;
      !move_to_the_next_cell(X, Y).

+!at(L) : at(L).
+!at(L)
   <- ?pos(L,X,Y);
      !check_battery_and_charge;
      move_to_the_next_cell(X,Y);
      !at(L).

+!move_to_charger //Doties uz uzlades staciju
   <- !choose_nearest_charger;  // Izveleties vistuvako uzlades staciju
      ?nearest_charger(Charger);
      ?pos(Charger, X, Y);
      !move_to_the_next_cell(X, Y).

+!choose_nearest_charger //Izveleties vistuvako uzlades staciju
   <- ?pos(car, X1, Y1);
      ?pos(charger1, X2, Y2);
      ?pos(charger2, X3, Y3);
      !distance(X1, Y1, X2, Y2, Dist1);
      !distance(X1, Y1, X3, Y3, Dist2);
      .print("Dist1: ", Dist1);
      .print("Dist2: ", Dist2);
      if (Dist1 <= Dist2) {
          +nearest_charger(charger1);
          .print("Choosing charger1");
      } else {
          +nearest_charger(charger2);
          .print("Choosing charger2");
      }.


+!charge_battery
   <- +charging; // pievienot parliecibu par to ka vajag uzladeties
      -charging;  // izdzest parliecibu par to ka vajag uzladeties
      +battery_load(100).

@lg[atomic]
+package(car) : not .desire(carry_to(_))
   <- !choose_nearest_post;
      ?nearest_post(Post);
      !carry_to(Post).

+!choose_nearest_post
   <- ?pos(car, X1, Y1);
      ?pos(post1, X2, Y2);
      ?pos(post2, X3, Y3);
      !distance(X1, Y1, X2, Y2, Dist1);
      !distance(X1, Y1, X3, Y3, Dist2);
      if (Dist1 <= Dist2) {
          +nearest_post(post1)
      } else {
          +nearest_post(post2)
      }.

+!distance(X1, Y1, X2, Y2, D) : true
   <- D = ((X1 - X2) * (X1 - X2) + (Y1 - Y2) * (Y1 - Y2)) ** 0.5.

+!carry_to(R)
   <- // atcereties vietu, kur vajag atgriezties
      ?pos(car,X,Y);
      -+pos(last,X,Y);

      // atnest pakotni uz vistivaku pasta staciju
      !take(pack,R);

      //atgrezties atpakal un tupninat meklet pakotnes
      !at(last);
      !check(cells).

+!take(S,L) : true
   <- !ensure_pick(S);
      !at(L);
      drop(S).

+!ensure_pick(S) : package(car)
   <- pick(package);
      !ensure_pick(S).
+!ensure_pick(_).

+!at(L) : at(L).
+!at(L)
   <- ?pos(L,X,Y);
      move_to_the_next_cell(X,Y);
      !at(L).


+!check_battery_and_charge
   <- ?battery_load(L);
      if (L <= 20 & not charging) {
          .suspend; // apturet pasreizejo nodomu
          !move_to_charger;
          !charge_battery;
          .resume // turpinat aptureto nodomu
      }.