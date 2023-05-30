import jason.asSyntax.*;

import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import jason.environment.Environment;
import jason.environment.grid.GridWorldModel;
import jason.environment.grid.GridWorldView;
import jason.environment.grid.Location;


import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.util.Random;
import java.util.logging.Logger;

public class AgentEnv extends Environment {


    public static final int GSize = 7; // rezga izmers
    public static final int PACK = 16; // pakotnes kods modeli
    public static final int CHARGER = 32; // 1 uzlades stacijas kods modeli
    public static final int CHARGER2 = 64; // 2 uzlades stacijas kods modeli

    public static final Term next_cell = Literal.parseLiteral("next(cell)");
    public static final Term pick_pack = Literal.parseLiteral("pick(package)");
    public static final Term drop_pack = Literal.parseLiteral("drop(pack)");
    public static final Term take_pack = Literal.parseLiteral("take(pack)");
    public static final Term charge = Literal.parseLiteral("charge()");

    public static final Literal pack1 = Literal.parseLiteral("package(car)");
    public static final Literal pack2 = Literal.parseLiteral("package(post1)");
    public static final Literal pack3 = Literal.parseLiteral("package(post2)");
    public static final Literal pack4 = Literal.parseLiteral("package(Charger1)");
    public static final Literal pack5 = Literal.parseLiteral("package(Charger2)");
    public static final Literal ch = Literal.parseLiteral("charger(ch)");
    public static final Literal ch2 = Literal.parseLiteral("charger(ch2)");


    private Image packageImage;
    private Image carImage;
    private Image poststation;
    private Image chargerImage;

    static Logger logger = Logger.getLogger(AgentEnv.class.getName());
    public static final int CELL_SIZE = 80;

    private AgentEnvModel model;
    private AgentEnvGui view;


    @Override
    public void init(String[] args) {
        model = new AgentEnvModel();
        view = new AgentEnvGui(model);
        model.setView(view);
        updatePercepts();
    }

    public AgentEnv() {
        try {
            packageImage = ImageIO.read(new File("package.png"));
            carImage = ImageIO.read(new File("car.png"));
            poststation = ImageIO.read(new File("poststation.png"));
            chargerImage = ImageIO.read(new File("charger.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean executeAction(String ag, Structure action) {
        logger.info(ag + " is: " + action);
        try {
            if (action.equals(next_cell)) {
                model.nextCell();
            } else if (action.getFunctor().equals("move_to_the_next_cell")) {
                if (model.getBatteryLoad() <= 20) {
                    // daties uz uzlades stacijas atrasanas vietu
                    model.moveToNextCell(model.getAgPos(3).x, model.getAgPos(3).y);
                    // Uzladet akumulatoru
                    model.chargeBattery();
                } else {
                    int x = (int) ((NumberTerm) action.getTerm(0)).solve();
                    int y = (int) ((NumberTerm) action.getTerm(1)).solve();
                    model.moveToNextCell(x, y);
                }
            } else if (action.equals(pick_pack)) {
                model.pickPackage();
            } else if (action.equals(drop_pack)) {
                model.dropPackage();
            } else if (action.equals(take_pack)) {
                model.takePackage();
            } else if (action.equals(charge)) {
                model.chargeBattery();
            } else {
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        updatePercepts();

        try {
            Thread.sleep(200);
        } catch (Exception e) {
        }

        if (model.getBatteryLoad() <= 0) {
            System.out.println("Agent battery level is 0%. Stopping the programm.");
            System.exit(0); // Iziet no programmas, kad akumulatora limenis ir 0%
        }

        informAgsEnvironmentChanged();

        return true;
    }

    /**
     * izveido agenta uztveri, pamatojoties uz AgentEnvModel
     */
    void updatePercepts() {
        clearPercepts();

        Location carLoc = model.getAgPos(0);
        Location post1Loc = model.getAgPos(1);
        Location post2Loc = model.getAgPos(2);
        Location chargerLoc = model.getAgPos(3);
        Location charger2Loc = model.getAgPos(4);

        Literal pos1 = Literal.parseLiteral("pos(car," + carLoc.x + "," + carLoc.y + ")");
        Literal pos2 = Literal.parseLiteral("pos(post1," + post1Loc.x + "," + post1Loc.y + ")");
        Literal pos3 = Literal.parseLiteral("pos(post2," + post2Loc.x + "," + post2Loc.y + ")");
        Literal pos4 = Literal.parseLiteral("pos(ch," + chargerLoc.x + "," + chargerLoc.y + ")");
        Literal pos5 = Literal.parseLiteral("pos(ch2," + charger2Loc.x + "," + charger2Loc.y + ")");

        addPercept(pos1);
        addPercept(pos2);
        addPercept(pos3);
        addPercept(pos4);
        addPercept(pos5);

        if (model.hasObject(PACK, carLoc)) {
            addPercept(pack1);
        }
        if (model.hasObject(PACK, post1Loc)) {
            addPercept(pack2);
        }
        if (model.hasObject(PACK, post2Loc)) {
            addPercept(pack3);
        }
        if (model.hasObject(PACK, chargerLoc)) {
            addPercept(pack4);
        }
        if (model.hasObject(PACK, charger2Loc)) {
            addPercept(pack5);
        }
    }

    class AgentEnvModel extends GridWorldModel {

        public int batteryLoad = 100; //Agenta akumulatoru iestatit uz 100%

        public static final int MErr = 2; // max kluda izveles pakotne
        int nerr; //pakotnes izveles meginajumu skaits
        boolean carHasPackage = false; // neatkatigi no ta vai agents nesa pakotni vai ne

        Random random = new Random(System.currentTimeMillis());

        private AgentEnvModel() {

            super(GSize, GSize, 10); // Agentu skaits

            // agentu atrasanas vietas
            try {
                setAgPos(0, 0, 0);

                Location post1Loc = new Location(GSize - 3, GSize - 6);
                setAgPos(1, post1Loc);

                Location post2Loc = new Location(GSize - 5, GSize - 4);
                setAgPos(2, post2Loc);

                Location chargerLoc = new Location(GSize - 2, GSize - 2);
                setAgPos(3, chargerLoc);

                Location charger2Loc = new Location(GSize - 5, GSize - 5);
                setAgPos(4, charger2Loc);

                setAgPos(5, GSize - 1, GSize - 1);// 2 agents
               /*setAgPos(6, GSize-2, GSize-1);// 3 agents
                setAgPos(7, GSize-3, GSize-1);// 4 agents
                setAgPos(8, GSize-4, GSize-1);// 5 agents
                setAgPos(9, GSize-2, GSize-1);// 6 agents
                setAgPos(10, GSize-1, GSize-2);// 7 agents
                setAgPos(11, GSize-4, GSize-1);// 8 agents
                setAgPos(12, GSize-4, GSize-3);// 9 agents
                setAgPos(13, GSize-5, GSize-1);// 10 agents*/

            } catch (Exception e) {
                e.printStackTrace();
            }

            // pakotnes atrasanas vieta
            add(PACK, 3, 0);
            add(PACK, 5, 0);
            add(PACK, 3, 1);
            add(PACK, 3, 3);
            add(PACK, 4, 4);
            add(PACK, 0, 5);
            add(PACK, 1, 6);

        }

        public int getBatteryLoad() {
            return batteryLoad;
        }

        void nextCell() throws Exception {
            Location car = getAgPos(0);
            car.x++;
            if (car.x == getWidth()) {
                car.x = 0;
                car.y++;
            }
            // agenta pozicijas parnesisana, kad vins sasniedz rezga beigas
            if (car.y == getHeight()) {
                car.y = 0;
            }
            setAgPos(0, car);
            setAgPos(1, getAgPos(1)); //lai pec tam agentu uzzimetu uz gui

            // Samazinat agenta akumulatora limenu uz 3%
            batteryLoad -= 3;

            // parliecinaties, ka akumulatora limenis nav mazaks par 0%
            if (batteryLoad < 0) {
                batteryLoad = 0;
            }

            // atjauninat gui, lai redzetu akamulatora limeni
            view.repaint();
        }

        void moveToNextCell(int x, int y) throws Exception {
            Location car = getAgPos(0);
            if (car.x < x)
                car.x++;
            else if (car.x > x)
                car.x--;
            if (car.y < y)
                car.y++;
            else if (car.y > y)
                car.y--;
            setAgPos(0, car);
            setAgPos(1, getAgPos(1));
        }


        void chargeBattery() throws Exception {
            Location carLoc = getAgPos(0);
            Location charger1Loc = getAgPos(3);
            Location charger2Loc = getAgPos(4);

            if (carLoc.equals(charger1Loc) || carLoc.equals(charger2Loc)) {
                // uzlades atruma un ilguma definejums
                int chargingSpeed = 2; // palielinat akumulatora limeni pa 2% katru reizi
                int chargingDelay = 100; // laiks start katri soli = 1 sekunde

                // Aprekinajums, cik daudz uzlades solu ir vajadzigs
                int steps = (100 - batteryLoad) / chargingSpeed;

                // pakapeniski uzladet akumulatoru
                for (int i = 0; i < steps; i++) {
                    batteryLoad += chargingSpeed;

                    //parzimet gui
                    view.repaint();

                    try {
                        Thread.sleep(chargingDelay);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                // parliecinaties ka uzlades limenis is 100%
                batteryLoad = 100;
            }
        }


        void pickPackage() {
            //agenta atrasanas vieta ir ari pakotne
            if (model.hasObject(PACK, getAgPos(0))) {
                if (random.nextBoolean() || nerr == MErr) {
                    remove(PACK, getAgPos(0));
                    nerr = 0;
                    carHasPackage = true;
                } else {
                    nerr++;
                }
            }
        }

        void dropPackage() {
            if (carHasPackage) {
                carHasPackage = false;
                add(PACK, getAgPos(0));
            }
        }

        void takePackage() {
            // pasta stacijai ir pakotne
            if (model.hasObject(PACK, getAgPos(1))) {
                remove(PACK, getAgPos(1));
            }
        }
    }

    class AgentEnvGui extends GridWorldView {

        public AgentEnvGui(AgentEnvModel model) {
            super(model, "Post Car Environment", 600);

            Timer timer = new Timer(50, new ActionListener() {

                public void actionPerformed(ActionEvent e) {
                    repaint();
                }
            });
            timer.start();

            setVisible(true);
            repaint();
        }

        @Override
        public void draw(Graphics g, int x, int y, int object) {
            switch (object) {
                case AgentEnv.PACK:
                    drawPackage(g, x, y);
                    break;
            }
        }


        @Override
        public void drawAgent(Graphics g, int x, int y, Color c, int id) {
            if (id == 0) {
                if (((AgentEnvModel) model).getBatteryLoad() > 0) { //zimet tikai tos agentu kam akumulatora limenis ir > par 0%
                    Location post1Loc = model.getAgPos(1);
                    Location post2Loc = model.getAgPos(2);
                    Location chargerLoc = model.getAgPos(3);
                    Location charger2Loc = model.getAgPos(4);

                    int carSize = CELL_SIZE * 3 / 4;
                    int postSize = CELL_SIZE * 3 / 5;
                    int chargerSize = CELL_SIZE * 3 / 6;

                    int carOffset = (CELL_SIZE - carSize) / 3;
                    int postOffset = (CELL_SIZE - postSize) / 4;
                    int chargerOffset = (CELL_SIZE - chargerSize) / 3;

                    g.drawImage(carImage, x * CELL_SIZE + carOffset, y * CELL_SIZE + carOffset, carSize, carSize, null);
                    g.setColor(Color.BLACK);
                    g.setFont(new Font("Arial", Font.BOLD, 14));
                    String batteryLoadStr = ((AgentEnvModel) model).getBatteryLoad() + "%";
                    int strWidth = g.getFontMetrics().stringWidth(batteryLoadStr);
                    int strX = x * CELL_SIZE + (CELL_SIZE - strWidth) / 2;
                    int strY = y * CELL_SIZE + CELL_SIZE / 5;
                    g.drawString(batteryLoadStr, strX, strY);
                    g.drawImage(poststation, post1Loc.x * CELL_SIZE + postOffset, post1Loc.y * CELL_SIZE + postOffset, postSize, postSize, null);
                    g.drawImage(poststation, post2Loc.x * CELL_SIZE + postOffset, post2Loc.y * CELL_SIZE + postOffset, postSize, postSize, null);
                    g.drawImage(chargerImage, chargerLoc.x * CELL_SIZE + chargerOffset, chargerLoc.y * CELL_SIZE + chargerOffset, chargerSize, chargerSize, null);
                    g.drawImage(chargerImage, charger2Loc.x * CELL_SIZE + chargerOffset, charger2Loc.y * CELL_SIZE + chargerOffset, chargerSize, chargerSize, null);
                }
            }
        }

        public void drawPackage(Graphics g, int x, int y) {
            int imageSize = CELL_SIZE / 2; // vartiba lai mainitu pakotnes izmeru atteciba pret cell_size
            int xOffset = (CELL_SIZE - imageSize) / 3;
            int yOffset = (CELL_SIZE - imageSize) / 3;
            g.drawImage(packageImage, x * CELL_SIZE + xOffset, y * CELL_SIZE + yOffset, imageSize, imageSize, null);
        }

    }

}