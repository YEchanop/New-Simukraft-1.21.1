package client.cn.kafei.simukraft.client.citizen;

import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib2.gui.ui.data.Transform2D;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.syncdata.ISubscription;
import common.cn.kafei.simukraft.citizen.CitizenInfoSlotLayout;
import common.cn.kafei.simukraft.citizen.CitizenInventory;
import common.cn.kafei.simukraft.config.ClientConfig;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.network.citizen.info.CitizenInfoResponsePacket;
import common.cn.kafei.simukraft.network.citizen.info.CitizenBehaviorActionPacket;
import common.cn.kafei.simukraft.network.citizen.info.CitizenSetSkinPacket;
import common.cn.kafei.simukraft.network.citizen.info.CitizenSkinRequestPacket;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 参考证件卡草图实现的 NPC 信息、装备与双背包一体界面。 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
public final class CitizenInfoUiRoot extends UIElement {
    private static final int FRAME_OUTER = 0xFF171919;
    private static final int FRAME_INNER = 0xFF767A7A;
    private static final int PANEL_LIGHT = 0xFFE0E0DB;
    private static final int PANEL_RECESSED = 0xFF969A99;
    private static final int PANEL_SLOT = 0xFF626968;
    private static final int TEXT_PRIMARY = 0xFF242727;
    private static final int TEXT_ON_DARK = 0xFFF1F1ED;
    private static final int CARD_PAPER = 0xFFF5F0E1;
    private static final int CARD_HEADER = 0xFF6D4C41;
    private static final int CARD_BORDER_OUTER = 0xFFD4C5A9;
    private static final int CARD_BORDER_INNER = 0xFFBEB09A;
    private static final int CARD_TAB_PAPER = 0xFFEDE8D9;
    private static final int CARD_TAB_BORDER = 0xFFB0A48E;
    private static final int CARD_TEXT = 0xFF3E2723;
    private static final int CARD_ACCENT = 0xFFC8A260;
    private static final int LEFT_PANEL_X = 78;
    private static final int LEFT_PANEL_WIDTH = 130;
    private static final int SECTION_HEADER_WIDTH = 98;
    private static final int LEFT_HEADER_X = LEFT_PANEL_X + LEFT_PANEL_WIDTH - SECTION_HEADER_WIDTH;
    private static final int LEFT_CONTENT_X = 84;
    private static final int LEFT_CONTENT_WIDTH = 118;
    private static final int[] ARMOR_INVENTORY_SLOTS = {
            CitizenInventory.HEAD_SLOT, CitizenInventory.CHEST_SLOT,
            CitizenInventory.LEGS_SLOT, CitizenInventory.FEET_SLOT
    };
    private static final EquipmentSlot[] ARMOR_EQUIPMENT_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final int LEFT_SECTION_Z = 4;
    private static final int DRAWER_WIDTH = 300;
    private static final int DRAWER_HEIGHT = 204;
    private static final float DRAWER_CLOSED_X = -DRAWER_WIDTH - 8.0F;
    private static final float DRAWER_OPEN_X = 68.0F;

    private final CitizenInfoResponsePacket packet;
    private final UIElement workspace = new UIElement().layout(layout -> {
        layout.width(CitizenInfoSlotLayout.WORKSPACE_WIDTH);
        layout.height(CitizenInfoSlotLayout.WORKSPACE_HEIGHT);
        layout.flexShrink(0);
    });
    private final CardDrawer cardDrawer;
    private boolean followEnabled;
    private boolean stayEnabled;
    private String currentSkinPath;
    /** 下载中心本会话已下载的皮肤名。 */
    private final Set<String> catalogDownloaded = new HashSet<>();
    /** 下载中心状态行引用，用于下载结果反馈。 */
    private UIElement catalogStatusLine;
    /** 下载中心查询状态：页号、关键词、排序（空=最新，likes=点赞）、类型（空=全部，steve/alex）与是否有下一页。 */
    private int catalogPage = 1;
    private String catalogKeyword = "";
    private String catalogSort = "";
    private String catalogType = "";
    private boolean catalogHasNext;
    private UIElement catalogPageLabel;

    public CitizenInfoUiRoot(CitizenInfoResponsePacket packet, CitizenInventory inventory, CitizenEntity owner) {
        this.packet = packet;
        this.followEnabled = packet.followingViewer();
        this.stayEnabled = packet.stayInPlace();
        this.currentSkinPath = packet.skinPath();
        layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
        });
        style(style -> style.backgroundTexture(new ColorRectTexture(0x78000000)));

        workspace.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        workspace.setOverflowVisible(true);
        addChild(workspace);

        buildLeftSection();
        buildRightSection();
        workspace.addChild(CitizenInfoSlotLayout.create(inventory, owner).style(style -> style.zIndex(8)));
        buildEquipmentIcons(inventory);
        buildLeftLabels(inventory);
        buildRightLabels();
        cardDrawer = new CardDrawer();
        buildSidebar();
        addEventListener(UIEvents.MOUSE_DOWN, ignored -> cardDrawer.close());
    }

    private void buildLeftSection() {
        addLeftSectionPanel(LEFT_HEADER_X, -3, SECTION_HEADER_WIDTH, 17, PANEL_LIGHT);
        addLeftSectionPanel(LEFT_PANEL_X, 14, LEFT_PANEL_WIDTH, 110, PANEL_RECESSED);
        addLeftSectionPanel(LEFT_PANEL_X, 126, LEFT_PANEL_WIDTH, 60, PANEL_LIGHT);
        addLeftSectionPanel(LEFT_PANEL_X, 188, LEFT_PANEL_WIDTH, 40, PANEL_RECESSED);

        CitizenEntityPreviewElement preview = new CitizenEntityPreviewElement(packet.entityId());
        preview.layout(layout -> absoluteLayout(layout, 104, 20, 78, 98));
        preview.style(style -> style.zIndex(6));
        workspace.addChild(preview);

    }

    private void buildRightSection() {
        workspace.addChild(metalPanel(286, 0, 98, 14, PANEL_LIGHT));
        workspace.addChild(metalPanel(210, 14, 174, 48, PANEL_RECESSED));
        workspace.addChild(metalPanel(210, 66, 174, 66, PANEL_LIGHT));
        workspace.addChild(metalPanel(312, 122, 72, 13, PANEL_LIGHT));
        workspace.addChild(metalPanel(210, 134, 174, 94, PANEL_RECESSED));
    }

    private void addLeftSectionPanel(int x, int y, int width, int height, int fillColor) {
        CitizenSectionPanelElement panel = new CitizenSectionPanelElement(fillColor, FRAME_OUTER, FRAME_INNER);
        panel.layout(layout -> absoluteLayout(layout, x, y, width, height));
        panel.style(style -> style.zIndex(LEFT_SECTION_Z));
        workspace.addChild(panel);
    }

    private void buildEquipmentIcons(CitizenInventory inventory) {
        addEquipmentIcon(inventory, CitizenInventory.HEAD_SLOT, InventoryMenu.EMPTY_ARMOR_SLOT_HELMET,
                CitizenInfoSlotLayout.EQUIPMENT_LEFT_X, CitizenInfoSlotLayout.HEAD_Y);
        addEquipmentIcon(inventory, CitizenInventory.CHEST_SLOT, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
                CitizenInfoSlotLayout.EQUIPMENT_LEFT_X, CitizenInfoSlotLayout.CHEST_Y);
        addEquipmentIcon(inventory, CitizenInventory.LEGS_SLOT, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
                CitizenInfoSlotLayout.EQUIPMENT_LEFT_X, CitizenInfoSlotLayout.LEGS_Y);
        addEquipmentIcon(inventory, CitizenInventory.FEET_SLOT, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS,
                CitizenInfoSlotLayout.EQUIPMENT_LEFT_X, CitizenInfoSlotLayout.FEET_Y);
        addMainHandIcon(inventory);
        addEquipmentIcon(inventory, CitizenInventory.OFF_HAND_SLOT, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD,
                CitizenInfoSlotLayout.EQUIPMENT_RIGHT_X, CitizenInfoSlotLayout.OFF_HAND_Y);
    }

    private void addMainHandIcon(CitizenInventory inventory) {
        CitizenEquipmentSlotIconElement element = CitizenEquipmentSlotIconElement.mainHand(
                inventory, CitizenInventory.MAIN_HAND_SLOT);
        element.layout(layout -> absoluteLayout(layout,
                CitizenInfoSlotLayout.EQUIPMENT_RIGHT_X + 1,
                CitizenInfoSlotLayout.MAIN_HAND_Y + 1, 16, 16));
        element.style(style -> style.zIndex(9));
        workspace.addChild(element);
    }

    private void addEquipmentIcon(CitizenInventory inventory,
                                  int inventorySlot,
                                  ResourceLocation icon,
                                  int slotX,
                                  int slotY) {
        CitizenEquipmentSlotIconElement element = new CitizenEquipmentSlotIconElement(inventory, inventorySlot, icon);
        element.layout(layout -> absoluteLayout(layout, slotX + 1, slotY + 1, 16, 16));
        element.style(style -> style.zIndex(9));
        workspace.addChild(element);
    }

    private void buildLeftLabels(CitizenInventory inventory) {
        workspace.addChild(text(Component.translatable("screen.simukraft.citizen_info.inventory.equipment"),
                LEFT_HEADER_X, 0, SECTION_HEADER_WIDTH, 10, TEXT_PRIMARY, TextTexture.TextType.NORMAL));
        workspace.addChild(statusBar(LEFT_CONTENT_X, 131, LEFT_CONTENT_WIDTH, 12, 0xFFC74450,
                packet.health() / 20.0D,
                Component.translatable("screen.simukraft.citizen_info.bar.health", CitizenInfoText.health(packet))));
        workspace.addChild(statusBar(LEFT_CONTENT_X, 144, LEFT_CONTENT_WIDTH, 12, 0xFFD39A35,
                packet.hunger() / 20.0D,
                Component.translatable("screen.simukraft.citizen_info.bar.hunger", CitizenInfoText.hunger(packet.hunger()))));
        if (packet.postpartumRemainingDays() > 0 || !"pregnancy.none".equals(packet.pregnancyStage())) {
            workspace.addChild(statusBar(LEFT_CONTENT_X, 157, LEFT_CONTENT_WIDTH, 12, 0xFFC56AA5,
                    packet.pregnancyProgress(),
                    Component.translatable("screen.simukraft.citizen_info.bar.pregnancy", CitizenInfoText.pregnancy(packet))));
        } else {
            workspace.addChild(armorStatusBar(inventory));
        }
        workspace.addChild(statusBar(LEFT_CONTENT_X, 170, LEFT_CONTENT_WIDTH, 12, 0xFF5A9D63,
                experienceProgress(packet),
                Component.translatable("screen.simukraft.citizen_info.bar.experience", CitizenInfoText.skill(packet))));
        workspace.addChild(toggleButton(LEFT_CONTENT_X, 192, "screen.simukraft.citizen_info.toggle.follow",
                CitizenBehaviorActionPacket.Action.TOGGLE_FOLLOW));
        workspace.addChild(toggleButton(LEFT_CONTENT_X, 209, "screen.simukraft.citizen_info.toggle.stay",
                CitizenBehaviorActionPacket.Action.TOGGLE_STAY));
    }

    private void buildRightLabels() {
        workspace.addChild(text(Component.translatable("screen.simukraft.citizen_info.inventory.npc"),
                286, 2, 98, 10, TEXT_PRIMARY, TextTexture.TextType.NORMAL));
        workspace.addChild(text(Component.translatable("container.inventory"),
                312, 124, 72, 9, TEXT_PRIMARY, TextTexture.TextType.NORMAL));
        workspace.addChild(text(Component.translatable("screen.simukraft.citizen_info.name", packet.name()),
                218, 72, 162, 12, TEXT_PRIMARY, TextTexture.TextType.LEFT));
        workspace.addChild(text(Component.translatable("screen.simukraft.citizen_info.gender", CitizenInfoText.gender(packet.gender())),
                218, 86, 162, 12, TEXT_PRIMARY, TextTexture.TextType.LEFT));
        workspace.addChild(text(Component.translatable("screen.simukraft.citizen_info.city", empty(packet.cityName())),
                218, 100, 162, 12, TEXT_PRIMARY, TextTexture.TextType.LEFT));
        workspace.addChild(text(Component.translatable("screen.simukraft.citizen_info.job", CitizenInfoText.job(packet)),
                218, 114, 162, 12, TEXT_PRIMARY, TextTexture.TextType.LEFT));
    }

    private UIElement toggleButton(int x, int y, String translationKey, CitizenBehaviorActionPacket.Action action) {
        UIElement button = panel(x, y, LEFT_CONTENT_WIDTH, 15, PANEL_LIGHT, FRAME_INNER);
        button.style(style -> style.zIndex(14));
        refreshToggleLabel(button, translationKey, action);
        button.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button != 0) {
                return;
            }
            if (action == CitizenBehaviorActionPacket.Action.TOGGLE_FOLLOW) {
                followEnabled = !followEnabled;
            } else {
                stayEnabled = !stayEnabled;
            }
            refreshToggleLabel(button, translationKey, action);
            PacketDistributor.sendToServer(new CitizenBehaviorActionPacket(packet.citizenId(), action));
            event.stopPropagation();
        });
        return button;
    }

    private void refreshToggleLabel(UIElement button, String translationKey, CitizenBehaviorActionPacket.Action action) {
        button.clearAllChildren();
        boolean enabled = action == CitizenBehaviorActionPacket.Action.TOGGLE_FOLLOW ? followEnabled : stayEnabled;
        UIElement label = text(Component.translatable(
                translationKey,
                        Component.translatable(enabled
                                ? "screen.simukraft.citizen_info.toggle.on"
                                : "screen.simukraft.citizen_info.toggle.off")),
                2, 2, LEFT_CONTENT_WIDTH - 4, 11,
                enabled ? 0xFF367943 : TEXT_PRIMARY,
                TextTexture.TextType.NORMAL);
        label.setAllowHitTest(false);
        button.addChild(label);
    }

    private void buildSidebar() {
        workspace.addChild(sidebarCard("identity", "screen.simukraft.citizen_info.menu.identity_short", 10));
        workspace.addChild(sidebarCard("residence", "screen.simukraft.citizen_info.menu.residence_short", 82));
        workspace.addChild(sidebarCard("work", "screen.simukraft.citizen_info.menu.work_short", 154));
        workspace.addChild(cardDrawer.backdrop);
        workspace.addChild(cardDrawer.drawer);
    }

    private UIElement sidebarCard(String id, String labelKey, int top) {
        UIElement button = panel(0, top, 46, 64, CARD_TAB_PAPER, CARD_TAB_BORDER);
        button.style(style -> style.zIndex(42));
        UIElement accent = absolute(1, 1, 44, 2);
        accent.setAllowHitTest(false);
        accent.style(style -> style.backgroundTexture(new ColorRectTexture(CARD_ACCENT)));
        button.addChild(accent);
        UIElement labelElement = text(Component.translatable(labelKey), 0, 4, 46, 56, CARD_TEXT, TextTexture.TextType.NORMAL);
        labelElement.setAllowHitTest(false);
        button.addChild(labelElement);
        button.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 0) {
                cardDrawer.open(id);
                event.stopPropagation();
            }
        });
        return button;
    }

    private UIElement statusBar(int x, int y, int width, int height, int fillColor, double progress, Component label) {
        UIElement bar = panel(x, y, width, height, PANEL_SLOT, FRAME_INNER);
        bar.style(style -> style.zIndex(10));
        refreshStatusBar(bar, width, height, fillColor, progress, label);
        return bar;
    }

    /** armorStatusBar：监听客户端真实装备槽，在当前界面内穿脱盔甲时实时刷新护甲值。 */
    private UIElement armorStatusBar(CitizenInventory inventory) {
        int initialArmor = Math.clamp(packet.armor(), 0, 20);
        UIElement bar = statusBar(LEFT_CONTENT_X, 157, LEFT_CONTENT_WIDTH, 12, 0xFF8293A1,
                initialArmor / 20.0D,
                Component.translatable("screen.simukraft.citizen_info.bar.armor", initialArmor + "/20"));
        int[] displayedArmor = {initialArmor};
        bar.addEventListener(UIEvents.TICK, ignored -> {
            int armor = armorValue(inventory);
            if (armor == displayedArmor[0]) {
                return;
            }
            displayedArmor[0] = armor;
            refreshStatusBar(bar, LEFT_CONTENT_WIDTH, 12, 0xFF8293A1, armor / 20.0D,
                    Component.translatable("screen.simukraft.citizen_info.bar.armor", armor + "/20"));
        });
        return bar;
    }

    /** armorValue：按原版装备属性计算 NPC 背包四个盔甲槽的 0-20 护甲值。 */
    private static int armorValue(CitizenInventory inventory) {
        if (inventory == null) {
            return 0;
        }
        double[] addValue = {0.0D};
        double[] totalMultiplier = {1.0D};
        for (int index = 0; index < ARMOR_INVENTORY_SLOTS.length; index++) {
            ItemStack stack = inventory.getItem(ARMOR_INVENTORY_SLOTS[index]);
            if (stack.isEmpty()) {
                continue;
            }
            stack.forEachModifier(ARMOR_EQUIPMENT_SLOTS[index], (attribute, modifier) -> {
                if (!Attributes.ARMOR.equals(attribute)) {
                    return;
                }
                switch (modifier.operation()) {
                    case ADD_VALUE -> addValue[0] += modifier.amount();
                    case ADD_MULTIPLIED_TOTAL -> totalMultiplier[0] *= 1.0D + modifier.amount();
                    case ADD_MULTIPLIED_BASE -> {
                    }
                }
            });
        }
        return Math.clamp((int) Math.floor(addValue[0] * totalMultiplier[0]), 0, 20);
    }

    /** refreshStatusBar：重建状态条填充和文字，不替换状态条本身及其事件监听。 */
    private void refreshStatusBar(UIElement bar, int width, int height, int fillColor, double progress, Component label) {
        bar.clearAllChildren();
        int fillWidth = Math.clamp((int) Math.round((width - 4) * Math.clamp(progress, 0.0D, 1.0D)), 0, width - 4);
        UIElement fill = absolute(2, 2, Math.max(1, fillWidth), height - 4);
        fill.setAllowHitTest(false);
        fill.style(style -> style.backgroundTexture(new ColorRectTexture(fillColor)));
        if (fillWidth == 0) {
            fill.setDisplay(false);
        }
        bar.addChild(fill);
        UIElement labelElement = absolute(2, 1, width - 4, height - 2);
        labelElement.setAllowHitTest(false);
        labelElement.style(style -> style.backgroundTexture(new TextTexture(label.getString())
                .setWidth(width - 4)
                .setType(TextTexture.TextType.NORMAL)
                .setColor(TEXT_ON_DARK)
                .setDropShadow(true)).zIndex(12));
        bar.addChild(labelElement);
    }

    private double experienceProgress(CitizenInfoResponsePacket packet) {
        if (packet.skillLevel() >= packet.skillMaxLevel()) {
            return 1.0D;
        }
        common.cn.kafei.simukraft.citizen.CitizenSkillSnapshot snapshot =
                new common.cn.kafei.simukraft.citizen.CitizenSkillSnapshot(
                        common.cn.kafei.simukraft.job.CityJobType.fromName(packet.jobType()),
                        Math.max(1, packet.skillLevel()), Math.max(0, packet.skillXp()), Math.max(1, packet.skillMaxLevel()));
        int required = common.cn.kafei.simukraft.citizen.CitizenLevelService.xpNeededForCurrentLevel(snapshot);
        return required <= 0 ? 1.0D
                : Math.clamp(common.cn.kafei.simukraft.citizen.CitizenLevelService.xpInCurrentLevel(snapshot) / (double) required, 0.0D, 1.0D);
    }

    /** metalPanel：创建双层金属边框，用于区分主卡片、信息卡片与凹陷槽位区。 */
    private static UIElement metalPanel(int x, int y, int width, int height, int faceColor) {
        UIElement frame = panel(x, y, width, height, FRAME_OUTER, FRAME_OUTER);
        frame.setAllowHitTest(false);
        UIElement face = absolute(2, 2, width - 4, height - 4);
        face.setAllowHitTest(false);
        face.style(style -> style.backgroundTexture(new GuiTextureGroup(
                new ColorRectTexture(faceColor), new ColorBorderTexture(1, FRAME_INNER))));
        frame.addChild(face);
        return frame;
    }

    private static UIElement panel(int x, int y, int width, int height, int color, int border) {
        UIElement panel = absolute(x, y, width, height);
        panel.style(style -> style.backgroundTexture(new GuiTextureGroup(
                new ColorRectTexture(color), new ColorBorderTexture(1, border))));
        return panel;
    }

    private static UIElement text(Component component, int x, int y, int width, int height,
                                  int color, TextTexture.TextType type) {
        UIElement element = absolute(x, y, width, height);
        element.setAllowHitTest(false);
        element.style(style -> style.backgroundTexture(new TextTexture(component.getString())
                .setWidth(width)
                .setType(type)
                .setColor(color)
                .setDropShadow(false)).zIndex(12));
        return element;
    }

    private static UIElement absolute(int x, int y, int width, int height) {
        UIElement element = new UIElement();
        element.layout(layout -> absoluteLayout(layout, x, y, width, height));
        return element;
    }

    private static void absoluteLayout(com.lowdragmc.lowdraglib2.gui.ui.style.LayoutStyle layout,
                                       float x, float y, float width, float height) {
        layout.positionType(TaffyPosition.ABSOLUTE);
        layout.left(x);
        layout.top(y);
        layout.width(Math.max(1.0F, width));
        layout.height(Math.max(1.0F, height));
    }

    private static String empty(String value) {
        return value == null || value.isBlank()
                ? Component.translatable("screen.simukraft.citizen_info.none").getString()
                : value;
    }

    private final class CardDrawer {
        private final UIElement backdrop = absolute(54, -3, 376, 231);
        private final UIElement drawer = absolute((int) DRAWER_OPEN_X, 12, DRAWER_WIDTH, DRAWER_HEIGHT);
        private ISubscription animationSubscription;

        private CardDrawer() {
            backdrop.style(style -> style.backgroundTexture(new ColorRectTexture(0x7A000000)).zIndex(30));
            backdrop.setDisplay(false);
            backdrop.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                close();
                event.stopPropagation();
            });
            drawer.style(style -> style.backgroundTexture(IGuiTexture.EMPTY).zIndex(50));
            drawer.transform(transform -> transform.translate(DRAWER_CLOSED_X - DRAWER_OPEN_X, 0.0F));
            drawer.addEventListener(UIEvents.MOUSE_DOWN, UIEvent::stopPropagation);
        }

        private void open(String id) {
            drawer.clearAllChildren();
            List<Component> lines = CitizenInfoText.cardLines(id, packet);
            CitizenSectionPanelElement frame = new CitizenSectionPanelElement(CARD_PAPER, CARD_BORDER_OUTER, CARD_BORDER_INNER);
            frame.layout(layout -> absoluteLayout(layout, 0, 0, DRAWER_WIDTH, DRAWER_HEIGHT));
            frame.style(style -> style.zIndex(0));
            drawer.addChild(frame);

            UIElement header = absolute(3, 3, DRAWER_WIDTH - 6, 20);
            header.setAllowHitTest(false);
            header.style(style -> style.backgroundTexture(new ColorRectTexture(CARD_HEADER)).zIndex(2));
            drawer.addChild(header);

            UIElement accent = absolute(3, 23, DRAWER_WIDTH - 6, 2);
            accent.setAllowHitTest(false);
            accent.style(style -> style.backgroundTexture(new ColorRectTexture(CARD_ACCENT)).zIndex(3));
            drawer.addChild(accent);

            Component title = lines.isEmpty()
                    ? Component.translatable("screen.simukraft.citizen_info.menu.identity")
                    : lines.getFirst();
            drawer.addChild(text(title, 4, 8, DRAWER_WIDTH - 8, 11, 0xFFFFFFFF, TextTexture.TextType.NORMAL));

            UIElement portraitFrame = panel(14, 32, 40, 40, CARD_ACCENT, CARD_HEADER);
            portraitFrame.setAllowHitTest(false);
            portraitFrame.style(style -> style.zIndex(4));
            drawer.addChild(portraitFrame);
            UIElement portrait = CitizenAvatarFactory.createHead(currentSkinPath, CARD_HEADER);
            portrait.layout(layout -> absoluteLayout(layout, 16, 34, 36, 36));
            portrait.setAllowHitTest(false);
            portrait.style(style -> style.zIndex(5));
            drawer.addChild(portrait);

            int lineY = 34;
            for (int index = 1; index < lines.size(); index++) {
                Component line = lines.get(index);
                UIElement lineElement = absolute(66, lineY, DRAWER_WIDTH - 80, 12);
                lineElement.setAllowHitTest(false);
                lineElement.style(style -> style.backgroundTexture(new TextTexture(line.getString())
                        .setWidth(DRAWER_WIDTH - 80)
                        .setType(TextTexture.TextType.LEFT)
                        .setColor(CARD_TEXT)
                        .setDropShadow(false)).zIndex(6));
                drawer.addChild(lineElement);
                lineY += 14;
            }

            if ("identity".equals(id)) {
                addSkinSection();
            }

            UIElement footer = absolute(12, DRAWER_HEIGHT - 10, DRAWER_WIDTH - 24, 1);
            footer.setAllowHitTest(false);
            footer.style(style -> style.backgroundTexture(new ColorRectTexture(0x80C8A260)).zIndex(3));
            drawer.addChild(footer);
            backdrop.setDisplay(true);
            animateTo(Transform2D.identity(), 0.22F, null);
        }

        /** addSkinSection：身份证卡片底部皮肤设置区，含当前皮肤与更换/刷新入口。 */
        private void addSkinSection() {
            drawer.addChild(text(Component.translatable("screen.simukraft.citizen_info.skin.label"), 14, 168, 40, 12, CARD_TEXT, TextTexture.TextType.NORMAL));
            String name = CitizenSkinLibrary.nameFromStoredPath(currentSkinPath);
            drawer.addChild(text(Component.literal(name != null ? name : Component.translatable("screen.simukraft.citizen_info.none").getString()),
                    52, 168, 148, 12, CARD_TEXT, TextTexture.TextType.LEFT));
            drawer.addChild(cardButton("screen.simukraft.citizen_info.skin.change", 206, 166, 42, 16, this::showSkinPicker));
            drawer.addChild(cardButton("screen.simukraft.citizen_info.skin.refresh", 252, 166, 36, 16, () -> {
                CitizenSkinLibrary.reload();
                PacketDistributor.sendToServer(new CitizenSkinRequestPacket());
                showSkinPicker();
            }));
        }

        /** showSkinPicker：更换皮肤子视图，列出 simukraftskins 文件夹中的皮肤。 */
        private void showSkinPicker() {
            drawer.clearAllChildren();

            CitizenSectionPanelElement frame = new CitizenSectionPanelElement(CARD_PAPER, CARD_BORDER_OUTER, CARD_BORDER_INNER);
            frame.layout(layout -> absoluteLayout(layout, 0, 0, DRAWER_WIDTH, DRAWER_HEIGHT));
            frame.style(style -> style.zIndex(0));
            drawer.addChild(frame);

            UIElement header = absolute(3, 3, DRAWER_WIDTH - 6, 20);
            header.setAllowHitTest(false);
            header.style(style -> style.backgroundTexture(new ColorRectTexture(CARD_HEADER)).zIndex(2));
            drawer.addChild(header);
            drawer.addChild(text(Component.translatable("screen.simukraft.citizen_info.skin.title"), 8, 8, DRAWER_WIDTH - 16, 11, 0xFFFFFFFF, TextTexture.TextType.NORMAL));

            UIElement accent = absolute(3, 23, DRAWER_WIDTH - 6, 2);
            accent.setAllowHitTest(false);
            accent.style(style -> style.backgroundTexture(new ColorRectTexture(CARD_ACCENT)).zIndex(3));
            drawer.addChild(accent);

            drawer.addChild(cardButton("screen.simukraft.citizen_info.skin.refresh", 14, 30, 60, 16, () -> {
                CitizenSkinLibrary.reload();
                PacketDistributor.sendToServer(new CitizenSkinRequestPacket());
                showSkinPicker();
            }));
            drawer.addChild(cardButton("screen.simukraft.citizen_info.skin.reset", 80, 30, 70, 16, () -> applySkin("")));
            drawer.addChild(cardButton("screen.simukraft.citizen_info.skin.download", 156, 30, 64, 16, this::showSkinDownloader));
            drawer.addChild(cardButton("screen.simukraft.citizen_info.back", 226, 30, 60, 16, () -> open("identity")));

            UIElement listPanel = new UIElement().layout(layout -> {
                layout.widthPercent(100);
                layout.flexDirection(FlexDirection.COLUMN);
                layout.gapAll(4);
                layout.paddingAll(4);
            });
            List<String> names = CitizenSkinLibrary.listNames();
            if (names.isEmpty()) {
                listPanel.addChild(cardLabel(Component.translatable("screen.simukraft.citizen_info.skin.empty"), CARD_TEXT));
            } else {
                for (String name : names) {
                    listPanel.addChild(skinRow(name));
                }
            }
            ScrollerView scroller = new ScrollerView();
            scroller.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL));
            scroller.layout(layout -> absoluteLayout(layout, 8, 52, DRAWER_WIDTH - 16, DRAWER_HEIGHT - 66));
            scroller.addScrollViewChild(listPanel);
            drawer.addChild(scroller);

            drawer.addChild(text(Component.translatable("screen.simukraft.citizen_info.skin.folder_hint",
                    CitizenSkinLibrary.rootDirectory().toString()), 12, DRAWER_HEIGHT - 10, DRAWER_WIDTH - 24, 9, CARD_TEXT, TextTexture.TextType.NORMAL));

            backdrop.setDisplay(true);
            animateTo(Transform2D.identity(), 0.22F, null);
        }

        /** skinRow：皮肤选择列表行，点击即应用并返回身份证卡片。 */
        private UIElement skinRow(String name) {
            String stored = CitizenSkinLibrary.storedPath(name);
            boolean current = name.equals(CitizenSkinLibrary.nameFromStoredPath(currentSkinPath));
            UIElement row = new UIElement().layout(layout -> {
                layout.widthPercent(100);
                layout.height(24);
                layout.flexDirection(FlexDirection.ROW);
                layout.gapAll(6);
                layout.alignItems(AlignItems.CENTER);
                layout.paddingAll(3);
            });
            row.style(style -> style.backgroundTexture(new ColorRectTexture(current ? 0xFFE8DFC8 : 0x00000000)));
            UIElement avatar = CitizenAvatarFactory.createHead(stored, CARD_ACCENT);
            avatar.layout(layout -> {
                layout.width(18);
                layout.height(18);
                layout.flexShrink(0);
            });
            avatar.setAllowHitTest(false);
            row.addChild(avatar);
            row.addChild(cardLabel(Component.literal(name), CARD_TEXT));
            if (current) {
                row.addChild(cardLabelFixed(Component.translatable("screen.simukraft.citizen_info.skin.current"), 0xFF6D4C41, 40));
            } else {
                Button useButton = new Button();
                useButton.setText(Component.translatable("screen.simukraft.citizen_info.skin.use"));
                useButton.setOnClick(event -> applySkin(stored));
                // 阻止冒泡，避免点「使用」时触发整行“应用皮肤”造成重复应用。
                useButton.addEventListener(UIEvents.MOUSE_DOWN, UIEvent::stopPropagation);
                useButton.layout(layout -> {
                    layout.width(48);
                    layout.height(16);
                    layout.flexShrink(0);
                });
                useButton.style(style -> style.zIndex(20));
                row.addChild(useButton);
            }
            if (CitizenSkinLibrary.hasLocalFile(name)) {
                Button deleteButton = new Button();
                deleteButton.setText(Component.translatable("screen.simukraft.citizen_info.skin.delete"));
                deleteButton.setOnClick(event -> deleteSkin(name));
                // 阻止冒泡，避免点删除时触发整行“应用皮肤”。
                deleteButton.addEventListener(UIEvents.MOUSE_DOWN, UIEvent::stopPropagation);
                deleteButton.layout(layout -> {
                    layout.width(40);
                    layout.height(16);
                    layout.flexShrink(0);
                });
                deleteButton.style(style -> style.zIndex(20));
                row.addChild(deleteButton);
            }
            row.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (event.button == 0) {
                    applySkin(stored);
                    event.stopPropagation();
                }
            });
            return row;
        }

        /** deleteSkin：删除本地皮肤文件并刷新列表；若为当前使用的皮肤则回退默认。 */
        private void deleteSkin(String name) {
            if (!CitizenSkinLibrary.deleteLocalSkin(name)) {
                return;
            }
            if (CitizenSkinLibrary.storedPath(name).equals(currentSkinPath)) {
                currentSkinPath = "";
                PacketDistributor.sendToServer(new CitizenSetSkinPacket(packet.citizenId(), ""));
            }
            showSkinPicker();
        }

        /** applySkin：本地更新皮肤并发送服务端，然后返回身份证卡片。 */
        private void applySkin(String skinPath) {
            currentSkinPath = skinPath;
            PacketDistributor.sendToServer(new CitizenSetSkinPacket(packet.citizenId(), skinPath));
            open("identity");
        }

        /** showSkinDownloader：皮肤下载中心子视图，支持关键词搜索、排序与翻页。 */
        private void showSkinDownloader() {
            drawer.clearAllChildren();

            CitizenSectionPanelElement frame = new CitizenSectionPanelElement(CARD_PAPER, CARD_BORDER_OUTER, CARD_BORDER_INNER);
            frame.layout(layout -> absoluteLayout(layout, 0, 0, DRAWER_WIDTH, DRAWER_HEIGHT));
            frame.style(style -> style.zIndex(0));
            drawer.addChild(frame);

            UIElement header = absolute(3, 3, DRAWER_WIDTH - 6, 20);
            header.setAllowHitTest(false);
            header.style(style -> style.backgroundTexture(new ColorRectTexture(CARD_HEADER)).zIndex(2));
            drawer.addChild(header);
            drawer.addChild(text(Component.translatable("screen.simukraft.citizen_info.skin.download_title"), 8, 8, 180, 11, 0xFFFFFFFF, TextTexture.TextType.NORMAL));
            drawer.addChild(cardButton("screen.simukraft.citizen_info.skin.catalog.api", 212, 5, 76, 16, this::showCatalogApiSettings));

            UIElement accent = absolute(3, 23, DRAWER_WIDTH - 6, 2);
            accent.setAllowHitTest(false);
            accent.style(style -> style.backgroundTexture(new ColorRectTexture(CARD_ACCENT)).zIndex(3));
            drawer.addChild(accent);

            drawer.addChild(cardButton("screen.simukraft.citizen_info.back", 226, 30, 60, 18, this::showSkinPicker));

            // 进入下载中心时清空上次会话的目录缩略图。
            CitizenSkinLibrary.clearCatalogThumbnails();

            // 关键词搜索行。
            TextField searchField = new TextField();
            searchField.setAnyString();
            searchField.setText(catalogKeyword);
            searchField.getTextFieldStyle().placeholder(Component.translatable("screen.simukraft.citizen_info.skin.catalog.search_placeholder"));
            searchField.layout(layout -> absoluteLayout(layout, 14, 30, 150, 18));
            searchField.style(style -> style.zIndex(10));
            drawer.addChild(searchField);
            drawer.addChild(cardButton("screen.simukraft.citizen_info.skin.catalog.search", 168, 30, 52, 18, () -> {
                catalogKeyword = searchField.getValue() == null ? "" : searchField.getValue().trim();
                catalogPage = 1;
                showSkinDownloader();
            }));

            // 排序切换 + 翻页行。
            String sortKey = "likes".equals(catalogSort)
                    ? "screen.simukraft.citizen_info.skin.catalog.sort.show_likes"
                    : "screen.simukraft.citizen_info.skin.catalog.sort.show_latest";
            drawer.addChild(cardButton(sortKey, 14, 52, 64, 18, () -> {
                catalogSort = "likes".equals(catalogSort) ? "" : "likes";
                catalogPage = 1;
                showSkinDownloader();
            }));
            drawer.addChild(cardButton("screen.simukraft.citizen_info.skin.catalog.prev", 88, 52, 56, 18, () -> {
                if (catalogPage > 1) {
                    catalogPage--;
                    showSkinDownloader();
                }
            }));
            catalogPageLabel = absolute(148, 52, 56, 18);
            catalogPageLabel.setAllowHitTest(false);
            catalogPageLabel.style(style -> style.zIndex(11));
            drawer.addChild(catalogPageLabel);
            updateCatalogPageLabel();
            drawer.addChild(cardButton("screen.simukraft.citizen_info.skin.catalog.next", 208, 52, 56, 18, () -> {
                if (catalogHasNext) {
                    catalogPage++;
                    showSkinDownloader();
                }
            }));

            // 类型过滤 + 状态行。
            String typeKey = "alex".equals(catalogType)
                    ? "screen.simukraft.citizen_info.skin.catalog.type_alex"
                    : "steve".equals(catalogType)
                            ? "screen.simukraft.citizen_info.skin.catalog.type_steve"
                            : "screen.simukraft.citizen_info.skin.catalog.type_all";
            drawer.addChild(cardButton(typeKey, 14, 74, 120, 18, () -> {
                catalogType = "alex".equals(catalogType) ? "" : "steve".equals(catalogType) ? "alex" : "steve";
                catalogPage = 1;
                showSkinDownloader();
            }));

            UIElement statusLine = absolute(140, 76, 146, 14);
            statusLine.setAllowHitTest(false);
            statusLine.style(style -> style.zIndex(11));
            drawer.addChild(statusLine);
            catalogStatusLine = statusLine;

            loadCatalog(statusLine);

            backdrop.setDisplay(true);
            animateTo(Transform2D.identity(), 0.22F, null);
        }

        /** updateCatalogPageLabel：刷新页码显示。 */
        private void updateCatalogPageLabel() {
            if (catalogPageLabel == null) {
                return;
            }
            catalogPageLabel.clearAllChildren();
            UIElement label = absolute(0, 2, 56, 14);
            label.setAllowHitTest(false);
            label.style(style -> style.backgroundTexture(new TextTexture(Component.translatable(
                    "screen.simukraft.citizen_info.skin.catalog.page", catalogPage).getString())
                    .setWidth(56)
                    .setType(TextTexture.TextType.NORMAL)
                    .setColor(CARD_TEXT)
                    .setDropShadow(false)).zIndex(12));
            catalogPageLabel.addChild(label);
        }

        /** loadCatalog：按当前查询条件拉取目录并构建列表。 */
        private void loadCatalog(UIElement statusLine) {
            String catalogUrl = ClientConfig.skinCatalogUrl();
            if (catalogUrl.isBlank()) {
                setDownloadStatus(statusLine, Component.translatable("screen.simukraft.citizen_info.skin.catalog.no_config"), 0xFFB00020);
                drawer.addChild(text(Component.translatable("screen.simukraft.citizen_info.skin.catalog.no_config_hint"), 14, 92, DRAWER_WIDTH - 28, 10, CARD_TEXT, TextTexture.TextType.NORMAL));
                return;
            }
            setDownloadStatus(statusLine, Component.translatable("screen.simukraft.citizen_info.skin.catalog.loading"), CARD_TEXT);
            CitizenSkinDownloadService.fetchCatalog(catalogUrl,
                    new CitizenSkinDownloadService.CatalogQuery(catalogPage, catalogKeyword, catalogSort, catalogType),
                    result -> {
                        if (!result.success()) {
                            String errorKey = "catalog_empty".equals(result.errorKey())
                                    ? "screen.simukraft.citizen_info.skin.catalog.empty"
                                    : "screen.simukraft.citizen_info.skin.download.error." + result.errorKey();
                            setDownloadStatus(statusLine, Component.translatable("screen.simukraft.citizen_info.skin.download_fail",
                                    Component.translatable(errorKey)), 0xFFB00020);
                            return;
                        }
                        catalogHasNext = result.hasNext();
                        updateCatalogPageLabel();
                        buildCatalogList(result.entries(), statusLine);
                    });
        }

        /** showCatalogApiSettings：API 管理子视图，列出已保存 API，可添加、切换、删除。 */
        private void showCatalogApiSettings() {
            drawer.clearAllChildren();

            CitizenSectionPanelElement frame = new CitizenSectionPanelElement(CARD_PAPER, CARD_BORDER_OUTER, CARD_BORDER_INNER);
            frame.layout(layout -> absoluteLayout(layout, 0, 0, DRAWER_WIDTH, DRAWER_HEIGHT));
            frame.style(style -> style.zIndex(0));
            drawer.addChild(frame);

            UIElement header = absolute(3, 3, DRAWER_WIDTH - 6, 20);
            header.setAllowHitTest(false);
            header.style(style -> style.backgroundTexture(new ColorRectTexture(CARD_HEADER)).zIndex(2));
            drawer.addChild(header);
            drawer.addChild(text(Component.translatable("screen.simukraft.citizen_info.skin.catalog.api_title"), 8, 8, 180, 11, 0xFFFFFFFF, TextTexture.TextType.NORMAL));

            UIElement accent = absolute(3, 23, DRAWER_WIDTH - 6, 2);
            accent.setAllowHitTest(false);
            accent.style(style -> style.backgroundTexture(new ColorRectTexture(CARD_ACCENT)).zIndex(3));
            drawer.addChild(accent);

            // 新增 API 行：名称 + 地址（右上返回按钮占用该行右侧）。
            drawer.addChild(cardButton("screen.simukraft.citizen_info.back", 226, 30, 60, 18, this::showSkinDownloader));
            TextField nameField = new TextField();
            nameField.setAnyString();
            nameField.getTextFieldStyle().placeholder(Component.translatable("screen.simukraft.citizen_info.skin.catalog.api_name_placeholder"));
            nameField.layout(layout -> absoluteLayout(layout, 14, 30, 64, 18));
            nameField.style(style -> style.zIndex(10));
            drawer.addChild(nameField);
            TextField urlField = new TextField();
            urlField.setAnyString();
            urlField.setText("https://");
            urlField.getTextFieldStyle().placeholder(Component.translatable("screen.simukraft.citizen_info.skin.catalog.api_placeholder"));
            urlField.layout(layout -> absoluteLayout(layout, 82, 30, 140, 18));
            urlField.style(style -> style.zIndex(10));
            drawer.addChild(urlField);

            UIElement statusLine = absolute(14, 180, DRAWER_WIDTH - 28, 14);
            statusLine.setAllowHitTest(false);
            statusLine.style(style -> style.zIndex(11));
            drawer.addChild(statusLine);
            catalogStatusLine = statusLine;

            drawer.addChild(cardButton("screen.simukraft.citizen_info.skin.catalog.api_add", 14, 52, 88, 18, () -> addCatalogApi(nameField, urlField, statusLine)));
            drawer.addChild(text(Component.translatable("screen.simukraft.citizen_info.skin.catalog.api_list"), 110, 54, 160, 12, CARD_TEXT, TextTexture.TextType.NORMAL));

            // 已保存 API 列表。
            UIElement listPanel = new UIElement().layout(layout -> {
                layout.widthPercent(100);
                layout.flexDirection(FlexDirection.COLUMN);
                layout.gapAll(4);
                layout.paddingAll(4);
            });
            List<ClientConfig.CatalogApi> apis = ClientConfig.catalogApis();
            String activeUrl = ClientConfig.skinCatalogUrl();
            if (apis.isEmpty()) {
                listPanel.addChild(cardLabel(Component.translatable("screen.simukraft.citizen_info.skin.catalog.api_empty"), CARD_TEXT));
            } else {
                for (ClientConfig.CatalogApi api : apis) {
                    listPanel.addChild(catalogApiRow(api, api.url().equals(activeUrl), statusLine));
                }
            }
            ScrollerView scroller = new ScrollerView();
            scroller.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL));
            scroller.layout(layout -> absoluteLayout(layout, 8, 66, DRAWER_WIDTH - 16, 84));
            scroller.addScrollViewChild(listPanel);
            drawer.addChild(scroller);

            // 底部：恢复默认 + 当前 API 提示。
            drawer.addChild(cardButton("screen.simukraft.citizen_info.skin.catalog.api_reset", 14, 156, 90, 18, () -> {
                ClientConfig.setSkinCatalogUrl(ClientConfig.DEFAULT_SKIN_CATALOG_URL);
                resetCatalogQuery();
                showSkinDownloader();
            }));
            drawer.addChild(text(Component.literal(Component.translatable("screen.simukraft.citizen_info.skin.catalog.api_current",
                    activeDisplayName(apis, activeUrl)).getString()), 110, 158, 170, 14, CARD_TEXT, TextTexture.TextType.LEFT));

            backdrop.setDisplay(true);
            animateTo(Transform2D.identity(), 0.22F, null);
        }

        /** catalogApiRow：单个 API 行（名称 + 使用/使用中 + 删除）。 */
        private UIElement catalogApiRow(ClientConfig.CatalogApi api, boolean active, UIElement statusLine) {
            UIElement row = new UIElement().layout(layout -> {
                layout.widthPercent(100);
                layout.height(24);
                layout.flexDirection(FlexDirection.ROW);
                layout.gapAll(6);
                layout.alignItems(AlignItems.CENTER);
                layout.paddingAll(3);
            });
            row.addChild(cardLabel(Component.literal(api.name().isBlank() ? api.url() : api.name()), CARD_TEXT));
            if (active) {
                row.addChild(cardLabelFixed(Component.translatable("screen.simukraft.citizen_info.skin.catalog.api_using"), 0xFF2E7D32, 44));
            } else {
                Button useButton = new Button();
                useButton.setText(Component.translatable("screen.simukraft.citizen_info.skin.catalog.api_use"));
                useButton.setOnClick(event -> {
                    ClientConfig.setSkinCatalogUrl(api.url());
                    resetCatalogQuery();
                    showSkinDownloader();
                });
                useButton.layout(layout -> {
                    layout.width(48);
                    layout.height(16);
                    layout.flexShrink(0);
                });
                useButton.style(style -> style.zIndex(20));
                row.addChild(useButton);
            }
            Button removeButton = new Button();
            removeButton.setText(Component.translatable("screen.simukraft.citizen_info.skin.catalog.api_delete"));
            removeButton.setOnClick(event -> {
                ClientConfig.removeCatalogApi(api.url());
                showCatalogApiSettings();
            });
            removeButton.layout(layout -> {
                layout.width(44);
                layout.height(16);
                layout.flexShrink(0);
            });
            removeButton.style(style -> style.zIndex(20));
            row.addChild(removeButton);
            return row;
        }

        /** addCatalogApi：校验并添加玩家输入的 API 到列表，留在本页供继续管理。 */
        private void addCatalogApi(TextField nameField, TextField urlField, UIElement statusLine) {
            String url = urlField.getValue() == null ? "" : urlField.getValue().trim();
            if (url.isBlank() || !CitizenSkinDownloadService.isValidCatalogUrl(url)) {
                setDownloadStatus(statusLine, Component.translatable("screen.simukraft.citizen_info.skin.catalog.api_invalid"), 0xFFB00020);
                return;
            }
            String name = nameField.getValue() == null ? "" : nameField.getValue().trim();
            ClientConfig.addCatalogApi(name, url);
            setDownloadStatus(statusLine, Component.translatable("screen.simukraft.citizen_info.skin.catalog.api_added"), 0xFF2E7D32);
            showCatalogApiSettings();
        }

        /** activeDisplayName：当前生效 API 的展示名，找不到时回退地址。 */
        private String activeDisplayName(List<ClientConfig.CatalogApi> apis, String activeUrl) {
            for (ClientConfig.CatalogApi api : apis) {
                if (api.url().equals(activeUrl)) {
                    return api.name().isBlank() ? api.url() : api.name();
                }
            }
            return activeUrl.isBlank() ? Component.translatable("screen.simukraft.citizen_info.none").getString() : activeUrl;
        }

        /** resetCatalogQuery：搜索/排序/翻页状态重置回首页。 */
        private void resetCatalogQuery() {
            catalogPage = 1;
            catalogKeyword = "";
            catalogSort = "";
            catalogType = "";
            catalogHasNext = false;
        }

        /** buildCatalogList：用目录条目构建可滚动皮肤列表，逐行异步加载缩略图。 */
        private void buildCatalogList(List<CitizenSkinDownloadService.CatalogEntry> entries, UIElement statusLine) {
            setDownloadStatus(statusLine, Component.translatable("screen.simukraft.citizen_info.skin.catalog.count", entries.size()), 0xFF2E7D32);
            UIElement listPanel = new UIElement().layout(layout -> {
                layout.widthPercent(100);
                layout.flexDirection(FlexDirection.COLUMN);
                layout.gapAll(4);
                layout.paddingAll(4);
            });
            for (CitizenSkinDownloadService.CatalogEntry entry : entries) {
                UIElement row = new UIElement().layout(layout -> {
                    layout.widthPercent(100);
                    layout.height(26);
                    layout.flexDirection(FlexDirection.ROW);
                    layout.gapAll(6);
                    layout.alignItems(AlignItems.CENTER);
                    layout.paddingAll(3);
                });
                populateCatalogRow(row, entry, false);
                listPanel.addChild(row);
                CitizenSkinDownloadService.fetchThumbnailAsync(entry.url(), entry.name(), ok -> populateCatalogRow(row, entry, ok));
            }
            ScrollerView scroller = new ScrollerView();
            scroller.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL));
            scroller.layout(layout -> absoluteLayout(layout, 8, 92, DRAWER_WIDTH - 16, 98));
            scroller.addScrollViewChild(listPanel);
            drawer.addChild(scroller);
        }

        /** populateCatalogRow：重建目录行（缩略图 + 名称 + 下载/已下载）。 */
        private void populateCatalogRow(UIElement row, CitizenSkinDownloadService.CatalogEntry entry, boolean thumbReady) {
            row.clearAllChildren();
            boolean downloaded = catalogDownloaded.contains(entry.name());
            UIElement avatar = thumbReady
                    ? CitizenAvatarFactory.createHead(CitizenSkinLibrary.catalogTextureLocation(entry.name()), CARD_ACCENT)
                    : CitizenAvatarFactory.createHead((String) null, CARD_ACCENT);
            avatar.layout(layout -> {
                layout.width(20);
                layout.height(20);
                layout.flexShrink(0);
            });
            avatar.setAllowHitTest(false);
            row.addChild(avatar);
            row.addChild(cardLabel(Component.literal(entry.displayName()), CARD_TEXT));
            if (downloaded) {
                row.addChild(cardLabelFixed(Component.translatable("screen.simukraft.citizen_info.skin.catalog.downloaded"), 0xFF2E7D32, 48));
            } else {
                Button downloadButton = new Button();
                downloadButton.setText(Component.translatable("screen.simukraft.citizen_info.skin.download_go"));
                downloadButton.setOnClick(event -> downloadCatalogSkin(entry, row));
                downloadButton.layout(layout -> {
                    layout.width(48);
                    layout.height(16);
                    layout.flexShrink(0);
                });
                downloadButton.style(style -> style.zIndex(20));
                row.addChild(downloadButton);
            }
        }

        /** downloadCatalogSkin：下载目录中的皮肤并刷新皮肤库。 */
        private void downloadCatalogSkin(CitizenSkinDownloadService.CatalogEntry entry, UIElement row) {
            CitizenSkinDownloadService.downloadAsync(entry.url(), entry.name(), result -> {
                boolean thumbReady = CitizenSkinLibrary.catalogTextureLocation(entry.name()) != null;
                if (result.success()) {
                    catalogDownloaded.add(entry.name());
                    CitizenSkinLibrary.reload();
                    populateCatalogRow(row, entry, thumbReady);
                    if (catalogStatusLine != null) {
                        setDownloadStatus(catalogStatusLine, Component.translatable("screen.simukraft.citizen_info.skin.download_ok", result.fileName()), 0xFF2E7D32);
                    }
                } else {
                    populateCatalogRow(row, entry, thumbReady);
                    if (catalogStatusLine != null) {
                        setDownloadStatus(catalogStatusLine, Component.translatable("screen.simukraft.citizen_info.skin.download_fail",
                                Component.translatable("screen.simukraft.citizen_info.skin.download.error." + result.errorKey())), 0xFFB00020);
                    }
                }
            });
        }

        /** setDownloadStatus：重建下载状态行文本。 */
        private void setDownloadStatus(UIElement statusLine, Component component, int color) {
            statusLine.clearAllChildren();
            UIElement label = absolute(0, 0, DRAWER_WIDTH - 28, 14);
            label.setAllowHitTest(false);
            label.style(style -> style.backgroundTexture(new TextTexture(component.getString())
                    .setWidth(DRAWER_WIDTH - 28)
                    .setType(TextTexture.TextType.LEFT)
                    .setColor(color)
                    .setDropShadow(false)).zIndex(12));
            statusLine.addChild(label);
        }

        private UIElement cardButton(String key, int x, int y, int width, int height, Runnable action) {
            Button button = new Button();
            button.setText(Component.translatable(key));
            button.setOnClick(event -> action.run());
            button.layout(layout -> absoluteLayout(layout, x, y, width, height));
            button.style(style -> style.zIndex(20));
            return button;
        }

        private Label cardLabel(Component component, int color) {
            Label label = new Label();
            label.setText(component);
            label.textStyle(style -> style.textColor(color).textShadow(false));
            label.layout(layout -> {
                layout.flex(1);
                layout.height(14);
            });
            return label;
        }

        private Label cardLabelFixed(Component component, int color, int width) {
            Label label = new Label();
            label.setText(component);
            label.textStyle(style -> style.textColor(color).textShadow(false));
            label.layout(layout -> {
                layout.width(width);
                layout.height(14);
                layout.flexShrink(0);
            });
            return label;
        }

        private void close() {
            if (!backdrop.isDisplayed()) {
                return;
            }
            Transform2D closed = Transform2D.identity().translate(DRAWER_CLOSED_X - DRAWER_OPEN_X, 0.0F);
            animateTo(closed, 0.18F, () -> backdrop.setDisplay(false));
        }

        /** animateTo：使用 LDLib2 渲染帧动画平移抽屉，避免每帧重新计算布局。 */
        private void animateTo(Transform2D target, float duration, Runnable finished) {
            if (animationSubscription != null) {
                animationSubscription.unsubscribe();
                animationSubscription = null;
            }
            var animation = drawer.animation()
                    .duration(duration)
                    .ease(progress -> 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress))
                    .style(PropertyRegistry.TRANSFORM_2D, target);
            animation.onFinished(ignored -> {
                animationSubscription = null;
                if (finished != null) {
                    finished.run();
                }
            });
            animationSubscription = animation.start();
        }
    }
}
