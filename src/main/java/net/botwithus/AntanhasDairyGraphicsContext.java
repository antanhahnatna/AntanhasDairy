package net.botwithus;

import net.botwithus.rs3.game.skills.Skills;
import net.botwithus.rs3.imgui.ImGui;
import net.botwithus.rs3.imgui.ImGuiWindowFlag;
import net.botwithus.rs3.script.ScriptConsole;
import net.botwithus.rs3.script.ScriptGraphicsContext;

import java.util.Arrays;
import java.util.LinkedList;

import static net.botwithus.rs3.script.ScriptConsole.println;

public class AntanhasDairyGraphicsContext extends ScriptGraphicsContext {

    private AntanhasDairy script;

    public AntanhasDairyGraphicsContext(ScriptConsole scriptConsole, AntanhasDairy script) {
        super(scriptConsole);
        this.script = script;
    }

    @Override
    public void drawSettings() {
        if (ImGui.Begin("Antanha's dairy", ImGuiWindowFlag.None.getValue())) {
            if (ImGui.BeginTabBar("My bar", ImGuiWindowFlag.None.getValue())) {
                if (ImGui.BeginTabItem("Main", ImGuiWindowFlag.None.getValue())) {
                    ImGui.Text("Script state: " + script.getBotState());
                    ImGui.BeginDisabled(script.getBotState() != AntanhasDairy.BotState.STOPPED);
                    if (ImGui.Button("Start")) {
                        //button has been clicked
                        script.setBotState(AntanhasDairy.BotState.SETUP);
                        script.logNames = new LinkedList<>();
                        script.logAmounts = new LinkedList<>();
                        script.startingExperience = Skills.COOKING.getSkill().getExperience();
                        script.startingTime = System.currentTimeMillis();
                    }
                    ImGui.EndDisabled();
                    ImGui.SameLine();
                    ImGui.BeginDisabled(script.getBotState() == AntanhasDairy.BotState.STOPPED);
                    if (ImGui.Button("Stop")) {
                        //has been clicked
                        script.setBotState(AntanhasDairy.BotState.STOPPED);
                        script.timeScriptWasLastActive = System.currentTimeMillis();
                    }
                    ImGui.EndDisabled();
                    ImGui.BeginDisabled(script.getBotState() != AntanhasDairy.BotState.STOPPED);
                    String[] productsCategories = Arrays.stream(AntanhasDairy.Products.class.getEnumConstants()).map(Enum::name).toArray(String[]::new);
                    int indexOfCurrentlySelectedProductsEnum = script.getSelectedProduct().ordinal();
                    script.setSelectedProduct(AntanhasDairy.Products.values()[ImGui.Combo("Select a product", indexOfCurrentlySelectedProductsEnum, productsCategories)]);
                    ImGui.EndDisabled();
                    ImGui.Separator();
                    ImGui.Text("Instructions:");
                    ImGui.Text("Set your last preset to withdraw what you want to be churned, then start the script.");
                    ImGui.EndTabItem();
                }
                if (ImGui.BeginTabItem("Stats", ImGuiWindowFlag.None.getValue())) {
                    ImGui.Text(script.logString());
                    ImGui.EndTabItem();
                }
                ImGui.EndTabBar();
            }
            ImGui.End();
        }

    }

    @Override
    public void drawOverlay() {
        super.drawOverlay();
    }
}
