package net.botwithus;

import net.botwithus.api.game.hud.inventories.Bank;
import net.botwithus.internal.scripts.ScriptDefinition;
import net.botwithus.rs3.events.impl.InventoryUpdateEvent;
import net.botwithus.rs3.game.Area;
import net.botwithus.rs3.game.Client;
import net.botwithus.rs3.game.Coordinate;
import net.botwithus.rs3.game.Item;
import net.botwithus.rs3.game.hud.interfaces.Component;
import net.botwithus.rs3.game.hud.interfaces.Interfaces;
import net.botwithus.rs3.game.inventories.Backpack;
import net.botwithus.rs3.game.movement.Movement;
import net.botwithus.rs3.game.movement.NavPath;
import net.botwithus.rs3.game.movement.TraverseEvent;
import net.botwithus.rs3.game.queries.builders.ItemQuery;
import net.botwithus.rs3.game.queries.builders.components.ComponentQuery;
import net.botwithus.rs3.game.queries.builders.items.InventoryItemQuery;
import net.botwithus.rs3.game.queries.builders.objects.SceneObjectQuery;
import net.botwithus.rs3.game.scene.entities.characters.player.LocalPlayer;
import net.botwithus.rs3.game.scene.entities.object.SceneObject;
import net.botwithus.rs3.game.skills.Skills;
import net.botwithus.rs3.script.Execution;
import net.botwithus.rs3.script.LoopingScript;
import net.botwithus.rs3.script.config.ScriptConfig;

import java.text.NumberFormat;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class AntanhasDairy extends LoopingScript {

    private BotState botState = BotState.STOPPED;
    private Random random = new Random();

    private Products selectedProduct = Products.CHEESEWHEEL;
    private String startingMaterial = "";
    LinkedList<String> logNames = new LinkedList<>();
    LinkedList<Integer> logAmounts = new LinkedList<>();
    int startingExperience = Skills.COOKING.getSkill().getExperience();
    long startingTime = System.currentTimeMillis();
    long timeScriptWasLastActive = System.currentTimeMillis();

    enum BotState {
        //define your own states here
        STOPPED,
        STOPPED_WRONGPRODUCTPICKED,
        STOPPED_LACKOFMATERIALS,
        STOPPED_WRONGMATERIAL,
        SETUP,
        MOVINGTOCHURN,
        CHURNING,
        MOVINGTOBANK,
        BANKING,
        //...
    }

    enum Products {
        //define your own states here
        POTOFCREAM("Pot of cream"),
        CREAMCHEESE("Cream cheese"),
        VANILLACREAMCHEESE("Vanilla cream cheese"),
        PATOFBUTTER("Pat of butter"),
        CHOCOLATECREAMCHEESE("Chocolate cream cheese"),
        STRAWBERRYCREAMCHEESE("Strawberry cream cheese"),
        CHEESEWHEEL("Cheese wheel");
        //...

        private final String name;

        Products(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public AntanhasDairy(String s, ScriptConfig scriptConfig, ScriptDefinition scriptDefinition) {
        super(s, scriptConfig, scriptDefinition);
        this.sgc = new AntanhasDairyGraphicsContext(getConsole(), this);
    }

    @Override
    public boolean initialize() {
        super.initialize();

        //this subscription updates the item log
        subscribe(InventoryUpdateEvent.class, inventoryUpdateEvent -> {
            //more events available at https://botwithus.net/javadoc/net.botwithus.rs3/net/botwithus/rs3/events/impl/package-summary.html
            //println("Chatbox message received: %s", chatMessageEvent.getMessage());
            //only update log if: a new item appears in the inv and the product isn't an empty bucket and the script isn't stopped and the bank interface isn't open
            if(inventoryUpdateEvent.getNewItem().getName() != null && !inventoryUpdateEvent.getNewItem().getName().equals("Bucket") && botState != BotState.STOPPED && botState != BotState.STOPPED_WRONGPRODUCTPICKED && botState != BotState.STOPPED_LACKOFMATERIALS && botState != BotState.STOPPED_WRONGMATERIAL && !Interfaces.isOpen(517)) {
                //println("New item: " + inventoryUpdateEvent.getNewItem().getName());
                //println("Old item: " + inventoryUpdateEvent.getOldItem().getName());
                int increment;
                if (inventoryUpdateEvent.getNewItem().getName().equals(inventoryUpdateEvent.getOldItem().getName()) && inventoryUpdateEvent.getNewItem().getStackSize() > inventoryUpdateEvent.getOldItem().getStackSize()) {
                    increment = inventoryUpdateEvent.getNewItem().getStackSize() - inventoryUpdateEvent.getOldItem().getStackSize();
                } else {
                    increment = inventoryUpdateEvent.getNewItem().getStackSize();
                }
                if (logNames.contains(inventoryUpdateEvent.getNewItem().getName())) {
                    logAmounts.set(logNames.indexOf(inventoryUpdateEvent.getNewItem().getName()), logAmounts.get(logNames.indexOf(inventoryUpdateEvent.getNewItem().getName())) + increment);
                } else {
                    logNames.push(inventoryUpdateEvent.getNewItem().getName());
                    logAmounts.push(increment);
                }
            }
        });

        return true;
    }

    @Override
    public void onLoop() {
        //println("onLoop()");

        //Loops every 100ms by default, to change:
        this.loopDelay = 500;
        LocalPlayer player = Client.getLocalPlayer();
        if (player == null || Client.getGameState() != Client.GameState.LOGGED_IN) {
            //wait some time so we dont immediately start on login.
            Execution.delay(random.nextLong(3000,7000));
            return;
        }

        switch (botState) {
            case STOPPED_WRONGPRODUCTPICKED:
            case STOPPED_LACKOFMATERIALS:
            case STOPPED_WRONGMATERIAL:
            case STOPPED:
                //do nothing
                println("We're idle!");
                Execution.delay(random.nextLong(1000,3000));
                break;
            //this only gets called when the start button is clicked, it can't clog onLoop() up
            case SETUP:
                botState = BotState.MOVINGTOBANK;
                break;
            //this clogs up the onLoop
            case MOVINGTOCHURN:
                Execution.delay(handleMoving(new Coordinate(3206, 3289, 0), player));
                if(botState != BotState.STOPPED && botState != BotState.STOPPED_WRONGPRODUCTPICKED && botState != BotState.STOPPED_LACKOFMATERIALS && botState != BotState.STOPPED_WRONGMATERIAL) botState = BotState.CHURNING;
                break;
            //notice that, if the script is running fine, this doesn't clog onLoop() up besides the brief pauses to emulate human-like behavior
            case CHURNING:
                handleChurning(player);
                break;
            //this clogs up the onLoop
            case MOVINGTOBANK:
                Execution.delay(handleMoving(new Coordinate(3214, 3258, 0), player));
                if(botState != BotState.STOPPED && botState != BotState.STOPPED_WRONGPRODUCTPICKED && botState != BotState.STOPPED_LACKOFMATERIALS && botState != BotState.STOPPED_WRONGMATERIAL) botState = BotState.BANKING;
                break;
            //this clogs up the onLoop
            case BANKING:
                Execution.delay(handleBanking());
                if(botState != BotState.STOPPED && botState != BotState.STOPPED_WRONGPRODUCTPICKED && botState != BotState.STOPPED_LACKOFMATERIALS && botState != BotState.STOPPED_WRONGMATERIAL) botState = BotState.MOVINGTOCHURN;
                break;
        }

    }

    private long handleChurning(LocalPlayer player) {
        /*
        //I moved the MOVINGTOBANK clause to inside the main loop below
        if(!Backpack.contains(startingMaterial)) {
            if (botState != BotState.STOPPED && botState != BotState.STOPPED_WRONGPRODUCTPICKED && botState != BotState.STOPPED_LACKOFMATERIALS && botState != BotState.STOPPED_WRONGMATERIAL) botState = BotState.MOVINGTOBANK;
            return random.nextLong(1500, 3000);
        }
        */
        //if we're idle
        if(player.getAnimationId() == -1 && !Interfaces.isOpen(1251)) {
            //and we have starting material in inv
            if(Backpack.contains(startingMaterial)) {
                //get to churning
                SceneObject churn = SceneObjectQuery.newQuery().name("Dairy churn").results().nearest();
                if (churn != null) {
                    churn.interact("Churn");
                    Execution.delayUntil(20000, () -> {
                        return Interfaces.isOpen(1371);
                    });
                    Execution.delay(random.nextLong(500, 1000));
                    //desiredProduct is the menu entry of the selected product in the churn menu; if it's not present, we know the user picked the wrong product for his raw material
                    Component desiredProduct = ComponentQuery.newQuery(1371).componentIndex(22).itemName(selectedProduct.getName()).results().first();
                    if (desiredProduct != null) {
                        desiredProduct.interact(desiredProduct.getOptions().get(0));
                        Execution.delayUntil(20000, () -> {
                            return ComponentQuery.newQuery(1370).componentIndex(30).results().first().getOptions().get(0).endsWith(selectedProduct.getName());
                        });
                        Execution.delay(random.nextLong(500, 1000));
                        Component beginButton = ComponentQuery.newQuery(1370).componentIndex(30).results().first();
                        //if the following condition is true, it means that the button to get started churning is greyed out despite the menu entry being present
                        if (beginButton.getOptions().get(0).startsWith("Make 0 ")) {
                            botState = BotState.STOPPED_WRONGMATERIAL;
                            return random.nextLong(1000, 2500);
                        } else {
                            beginButton.interact(beginButton.getOptions().get(0));
                            Execution.delay(random.nextLong(500, 1000));
                        }
                    } else {
                        botState = BotState.STOPPED_WRONGPRODUCTPICKED;
                        return random.nextLong(1000, 2500);
                    }
                }
            } else {
                if (botState != BotState.STOPPED && botState != BotState.STOPPED_WRONGPRODUCTPICKED && botState != BotState.STOPPED_LACKOFMATERIALS && botState != BotState.STOPPED_WRONGMATERIAL) botState = BotState.MOVINGTOBANK;
                return random.nextLong(1500, 3000);
            }
        }
        return random.nextLong(1500, 3000);
    }

    private long handleMoving(Coordinate whichCoordinate, LocalPlayer player) {

        //if the player is already near the destination point, no need to move
        if(player.distanceTo(whichCoordinate) < 10) {
            return random.nextLong(1500,3000);
        }
        //traverse() seems to have a slight bug (or possibly feature?) where it doesn't get started if the player is currently busy with some things like mining or woodcutting. so here's a brief check to make it click somewhere unless it's idle
        Coordinate vicinity = new Area.Circular(player.getCoordinate(), 1).getRandomWalkableCoordinate();
        //there used to be a walkTo(Coordinate, boolean) among other versions but they were removed after deprecation so I'm using the only version left, walkTo(int, int, boolean)
        Movement.walkTo(vicinity.getX(), vicinity.getY(), false);
        Execution.delay(random.nextLong(1000,2000));
        //Movement.traverse(NavPath.resolve(new Area.Circular(whichCoordinate, 2).getRandomWalkableCoordinate()));
        TraverseEvent.State moveState = Movement.traverse(NavPath.resolve(new Area.Circular(whichCoordinate, 1).getRandomWalkableCoordinate()));
        //sometimes the traversal can fail because e.g. a door closed shut right as we're about to cross it, so add a little failsafe just in case
        switch (moveState) {
            case FINISHED:
                println("handleMoving() | Successfully moved to the area.");
                return random.nextLong(1500,3000);

            case NO_PATH:
            case FAILED:
                println("handleMoving() | Path state: " + moveState.toString());
                println("handleMoving() | No path found or movement failed. Please navigate to the correct area manually.");
                //botState = BotState.STOPPED;
                Movement.traverse(NavPath.resolve(new Area.Circular(whichCoordinate, 2).getRandomWalkableCoordinate()));
                return random.nextLong(1500,3000);

            default:
                println("handleMoving() | Unexpected state: " + moveState.toString());
                //botState = BotState.STOPPED;
                Movement.traverse(NavPath.resolve(new Area.Circular(whichCoordinate, 2).getRandomWalkableCoordinate()));
                return random.nextLong(1500,3000);
        }
        //return random.nextLong(1500,3000);
    }

    private long handleBanking() {

        Execution.delayUntil(20000, () -> Bank.loadLastPreset());
        Execution.delay(random.nextLong(1500,3000));
        //the rest of this method is to keep track of what is the raw material which the user withdraws
        Item firstItem = InventoryItemQuery.newQuery(93).ids(1927, 2130, 6697).results().first();   //bucket of milk, pot of cream and pat of butter
        if(firstItem == null) {
            botState = BotState.STOPPED_LACKOFMATERIALS;
        } else {
            startingMaterial = firstItem.getName();
        }
        return random.nextLong(1500,3000);
    }

    //the big String containing all text in the stats
    public String logString() {
        int xpGained = Skills.COOKING.getSkill().getExperience() - startingExperience;
        String bigString = "Time elapsed: " + timeElapsed() + "\n";
        bigString = bigString + "Experience gained: " + xpGained + " (" + calculatePerHour(xpGained) + " / hr)\n";
        for(var i = logNames.size() - 1; i >= 0; i--) {
            bigString = bigString + logNames.get(i) + " x " + logAmounts.get(i) + " (" + calculatePerHour(logAmounts.get(i)) + " / hr)\n";
        }
        return bigString;
    }

    //used by logString()
    private String calculatePerHour(int toBeCalculated) {
        long timeToConsider = botState != BotState.STOPPED && botState != BotState.STOPPED_WRONGPRODUCTPICKED && botState != BotState.STOPPED_LACKOFMATERIALS && botState != BotState.STOPPED_WRONGMATERIAL ? System.currentTimeMillis() : timeScriptWasLastActive;

        long timeElapsedMillis = timeToConsider - startingTime;

        long xpPerHour = (long) (toBeCalculated / (timeElapsedMillis / 3600000.0));

        NumberFormat numberFormat = NumberFormat.getInstance();
        String formattedPerHour = numberFormat.format(xpPerHour);

        return formattedPerHour;
    }

    //used by logString()
    private String timeElapsed() {
        long endingTime = botState != BotState.STOPPED && botState != BotState.STOPPED_WRONGPRODUCTPICKED && botState != BotState.STOPPED_LACKOFMATERIALS && botState != BotState.STOPPED_WRONGMATERIAL ? System.currentTimeMillis() : timeScriptWasLastActive;;
        long elapsedTime = endingTime - startingTime;

        long hours = TimeUnit.MILLISECONDS.toHours(elapsedTime);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTime) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTime) % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public BotState getBotState() {
        return botState;
    }

    public void setBotState(BotState botState) {
        this.botState = botState;
    }

    public Products getSelectedProduct() {
        return selectedProduct;
    }

    public void setSelectedProduct(Products selectedProduct) {
        this.selectedProduct = selectedProduct;
    }

}
