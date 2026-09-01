package client.cn.kafei.simukraft.client.citizen.ai;

import common.cn.kafei.simukraft.config.ClientConfig;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * AiModelPickerDialog: 模型选择对话框（Task 5）。
 *
 * <p>打开前调用方应确保至少存在一个已配置的端点；空态时展示友好提示与「AI 设置」入口。
 * 工厂方法 {@link #create(Runnable, BiConsumer, Runnable)} 返回带遮罩的 overlay：
 * <ul>
 *   <li>{@code onClose} —— 取消/关闭</li>
 *   <li>{@code onConfirm} —— 用户点「开始对话」时回调 (endpoint, model)</li>
 *   <li>{@code onOpenSettings} —— 点「AI 设置」齿轮时回调（由调用方打开 Task 4 面板）</li>
 * </ul>
 * 默认选中：优先读取 {@code CITIZEN_AI_DEFAULT_ENDPOINT_ID + CITIZEN_AI_DEFAULT_MODEL_ID}；
 * 组合不存在时自动选择第一个启用端点的第一个启用模型。
 */
@SuppressWarnings({"null", "DataFlowIssue"})
@OnlyIn(Dist.CLIENT)
public final class AiModelPickerDialog {

    private static final int DIALOG_W = 560;
    private static final int DIALOG_H = 380;
    private static final int DIALOG_ACCENT = 0xFF6D4C41;
    private static final int DIALOG_PAPER = 0xFFF5F0E1;
    private static final int DIALOG_TEXT = 0xFF3E2723;
    private static final int DIALOG_SUBTEXT = 0xFF5D4037;
    private static final int SELECT_BG = 0xFFD7CCC8;
    private static final int DEFAULT_BADGE_BG = 0xFFE65100;
    private static final java.util.Map<String, Boolean> COLLAPSED_STATE = new java.util.HashMap<>();

    private AiModelPickerDialog() {
    }

    /** create: 便捷工厂（onOpenSettings 为空则隐藏齿轮按钮）。 */
    public static UIElement create(Runnable onClose, BiConsumer<ClientConfig.AiEndpoint, ClientConfig.AiModel> onConfirm) {
        return create(onClose, onConfirm, null);
    }

    /** create: 带遮罩 overlay 的模型选择对话框。 */
    public static UIElement create(Runnable onClose,
                                   BiConsumer<ClientConfig.AiEndpoint, ClientConfig.AiModel> onConfirm,
                                   Runnable onOpenSettings) {
        UIElement overlay = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.right(0);
            layout.bottom(0);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
        }).style(style -> style.backgroundTexture(new ColorRectTexture(0x80000000)));
        overlay.addEventListener(UIEvents.MOUSE_DOWN, e -> e.stopPropagation());

        UIElement dialog = new UIElement().layout(layout -> {
            layout.width(DIALOG_W);
            layout.height(DIALOG_H);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.paddingAll(8);
            layout.gapAll(6);
            layout.alignItems(AlignItems.STRETCH);
        }).style(style -> style.backgroundTexture(new ColorRectTexture(DIALOG_PAPER)));

        // ===== 标题栏 =====
        UIElement header = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(22);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.paddingAll(4);
        }).style(style -> style.backgroundTexture(new ColorRectTexture(DIALOG_ACCENT)));
        Label title = new Label();
        title.setText(Component.translatable("screen.simukraft.citizen_ai.pick.title"));
        title.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(false).fontSize(11.0F));
        title.layout(layout -> { layout.flex(1); layout.height(14); });
        header.addChild(title);
        Button closeBtn = new Button();
        closeBtn.setText(Component.literal("×"));
        closeBtn.setOnClick(e -> onClose.run());
        closeBtn.layout(layout -> { layout.width(20); layout.height(16); layout.flexShrink(0); });
        header.addChild(closeBtn);
        dialog.addChild(header);

        // ===== 搜索框 =====
        TextField searchField = new TextField();
        searchField.setAnyString();
        searchField.getTextFieldStyle().placeholder(Component.translatable("screen.simukraft.citizen_ai.pick.search"));
        searchField.layout(layout -> { layout.widthPercent(100); layout.height(20); });
        dialog.addChild(searchField);

        // ===== 端点 + 模型滚动列表 =====
        UIElement listPanel = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(4);
            layout.paddingAll(3);
        });
        ScrollerView scroller = new ScrollerView();
        scroller.scrollerStyle(s -> s.mode(ScrollerMode.VERTICAL));
        scroller.layout(layout -> { layout.flex(1); layout.widthPercent(100); });
        scroller.addScrollViewChild(listPanel);
        dialog.addChild(scroller);

        // ===== 底部按钮 =====
        UIElement actionRow = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(6);
            layout.alignItems(AlignItems.CENTER);
            layout.paddingTop(2);
            layout.height(26);
        });
        Button startBtn = smallButton("screen.simukraft.citizen_ai.pick.start", 96);
        Button cancelBtn = smallButton("screen.simukraft.citizen_ai.pick.cancel", 72);
        UIElement gearSpacer = new UIElement().layout(layout -> { layout.flex(1); layout.height(18); });
        Button settingsBtn = smallButton("screen.simukraft.citizen_ai.pick.settings", 88);
        if (onOpenSettings == null) {
            settingsBtn.setVisible(false);
        } else {
            settingsBtn.setOnClick(e -> onOpenSettings.run());
        }
        actionRow.addChild(startBtn);
        actionRow.addChild(cancelBtn);
        actionRow.addChild(gearSpacer);
        actionRow.addChild(settingsBtn);
        dialog.addChild(actionRow);

        overlay.addChild(dialog);

        // ===== 状态 + 重建逻辑 =====
        final String[] selectedEpId = {null};
        final String[] selectedModelId = {null};
        // 折叠态：key=endpointId，false=展开（默认），true=折叠
        final Object[] collapsedHolder = new Object[1];
        collapsedHolder[0] = COLLAPSED_STATE;

        // 用 holder 数组避免匿名内部类初始化前自引用
        final Runnable[] rebuildRef = new Runnable[1];
        rebuildRef[0] = () -> {
            listPanel.clearAllChildren();
            String q = searchField.getValue() == null ? "" : searchField.getValue().toLowerCase(Locale.ROOT).trim();
            List<ClientConfig.AiEndpoint> eps = ClientConfig.listAiEndpoints();
            boolean anyEnabled = false;
            for (ClientConfig.AiEndpoint ep : eps) {
                List<ClientConfig.AiModel> visibleModels = new ArrayList<>();
                for (ClientConfig.AiModel m : ep.models()) {
                    if (!m.enabled()) continue;
                    String hay = (ep.alias() + " " + ep.baseUrl() + " " + m.id() + " " + m.name()).toLowerCase(Locale.ROOT);
                    if (!q.isEmpty() && !hay.contains(q)) continue;
                    visibleModels.add(m);
                    anyEnabled = true;
                }
                if (visibleModels.isEmpty()) continue;
                listPanel.addChild(endpointSection(ep, visibleModels, selectedEpId, selectedModelId, rebuildRef[0], collapsedHolder, q));
            }
            if (!anyEnabled) {
                Label empty = new Label();
                empty.setText(Component.translatable("screen.simukraft.citizen_ai.pick.empty"));
                empty.textStyle(s -> s.textColor(DIALOG_SUBTEXT).textShadow(false).fontSize(10.0F));
                empty.layout(l -> { l.widthPercent(100); l.height(16); });
                listPanel.addChild(empty);
            }
            refreshStartButton(startBtn, selectedEpId, selectedModelId);
        };

        cancelBtn.setOnClick(e -> onClose.run());
        startBtn.setOnClick(e -> {
            ClientConfig.AiEndpoint ep = findEndpoint(selectedEpId[0]);
            ClientConfig.AiModel model = ep == null ? null : findModel(ep, selectedModelId[0]);
            if (ep != null && model != null) {
                ClientConfig.setLastModelByEndpoint(ep.id(), model.id());
                onConfirm.accept(ep, model);
            }
        });
        searchField.setTextResponder(t -> rebuildRef[0].run());

        // ===== 默认选中 + 初始渲染 =====
        resolveDefaultSelection(selectedEpId, selectedModelId);
        rebuildRef[0].run();

        return overlay;
    }

    // ======================================================================
    // 内部辅助
    // ======================================================================

    /** endpointSection: 单个端点区块：头行（折叠图标/alias/host/协议/上次标签/默认徽标）+ 模型行。 */
    private static UIElement endpointSection(ClientConfig.AiEndpoint ep,
                                             List<ClientConfig.AiModel> models,
                                             String[] selectedEpId,
                                             String[] selectedModelId,
                                             Runnable rebuildRef,
                                             Object[] collapsedHolder,
                                             String q) {
        // 读取折叠态：false=展开（默认），true=折叠
        @SuppressWarnings("unchecked")
        java.util.Map<String, Boolean> collapsed = (java.util.Map<String, Boolean>) collapsedHolder[0];
        boolean isCollapsed = collapsed.getOrDefault(ep.id(), Boolean.FALSE);

        // 搜索非空且端点有 visibleModels 时强制忽略 collapsed
        boolean forceExpand = !q.isEmpty() && !models.isEmpty();
        boolean shouldRenderModels = forceExpand || !isCollapsed;

        UIElement section = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(2);
            layout.paddingAll(3);
        }).style(s -> s.backgroundTexture(new ColorRectTexture(0xFFEDE0C8)));

        // 切换折叠态的回调
        Runnable toggleCollapsed = () -> {
            collapsed.put(ep.id(), !collapsed.getOrDefault(ep.id(), Boolean.FALSE));
            rebuildRef.run();
        };

        // 端点头行
        UIElement head = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(20);
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(6);
            layout.alignItems(AlignItems.CENTER);
        });
        // 头行整体点击切换折叠
        head.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) toggleCollapsed.run();
        });

        // 折叠图标 Label：▼=展开 / ▶=折叠
        Label collapseLbl = new Label();
        collapseLbl.setText(Component.literal(isCollapsed ? "\u25B6" : "\u25BC"));
        collapseLbl.textStyle(s -> s.textColor(DIALOG_SUBTEXT).textShadow(false).fontSize(10.0F));
        collapseLbl.layout(l -> { l.width(14); l.height(14); l.flexShrink(0); });
        collapseLbl.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) toggleCollapsed.run();
        });
        head.addChild(collapseLbl);

        Label aliasLbl = new Label();
        String alias = (ep.alias() == null || ep.alias().isBlank()) ? "(未命名)" : ep.alias();
        aliasLbl.setText(Component.literal(alias));
        aliasLbl.textStyle(s -> s.textColor(DIALOG_TEXT).textShadow(false).fontSize(11.0F).textWrap(TextWrap.HIDE));
        aliasLbl.layout(l -> { l.width(110); l.height(14); l.flexShrink(0); });
        head.addChild(aliasLbl);

        Label hostLbl = new Label();
        hostLbl.setText(Component.literal(extractHost(ep.baseUrl())));
        hostLbl.textStyle(s -> s.textColor(DIALOG_SUBTEXT).textShadow(false).fontSize(9.0F).textWrap(TextWrap.HIDE));
        hostLbl.layout(l -> { l.flex(1); l.height(12); });
        head.addChild(hostLbl);

        Label protoLbl = new Label();
        String proto = (ep.protocol() == null || ep.protocol().isBlank()) ? "openai" : ep.protocol();
        protoLbl.setText(Component.literal(proto));
        protoLbl.textStyle(s -> s.textColor(DIALOG_SUBTEXT).textShadow(false).fontSize(9.0F));
        protoLbl.layout(l -> { l.width(56); l.height(12); l.flexShrink(0); });
        head.addChild(protoLbl);

        // 「上次：xxx」微标签
        String lastModel = ClientConfig.getLastModelByEndpoint(ep.id());
        if (lastModel != null && !lastModel.isBlank()) {
            String display = lastModel.length() > 20 ? lastModel.substring(0, 17) + ".." : lastModel;
            Label lastLbl = new Label();
            lastLbl.setText(Component.literal("\u4E0A\u6B21\uFF1A" + display));
            lastLbl.textStyle(s -> s.textColor(DIALOG_SUBTEXT).textShadow(false).fontSize(9.0F));
            lastLbl.layout(l -> { l.height(12); l.flexShrink(0); });
            head.addChild(lastLbl);
        }

        section.addChild(head);

        // 模型行（仅在展开或搜索强制展开时渲染）
        if (shouldRenderModels) {
            for (ClientConfig.AiModel m : models) {
                boolean selected = ep.id().equals(selectedEpId[0]) && m.id().equals(selectedModelId[0]);
                section.addChild(modelRow(ep, m, selected, () -> {
                    selectedEpId[0] = ep.id();
                    selectedModelId[0] = m.id();
                    ClientConfig.setLastModelByEndpoint(ep.id(), m.id());
                    rebuildRef.run();
                }));
            }
        }
        return section;
    }

    /** modelRow: 单个模型行，点击选中（高亮），默认模型带徽标。 */
    private static UIElement modelRow(ClientConfig.AiEndpoint ep, ClientConfig.AiModel m,
                                      boolean selected, Runnable onSelect) {
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(22);
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(6);
            layout.alignItems(AlignItems.CENTER);
            layout.paddingAll(3);
            layout.paddingLeft(14);
        });
        row.style(s -> s.backgroundTexture(new ColorRectTexture(selected ? SELECT_BG : 0x00000000)));
        row.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) onSelect.run();
        });

        Label checkLbl = new Label();
        checkLbl.setText(Component.literal(selected ? "●" : "○"));
        checkLbl.textStyle(s -> s.textColor(selected ? DIALOG_ACCENT : DIALOG_SUBTEXT).textShadow(false).fontSize(11.0F));
        checkLbl.layout(l -> { l.width(16); l.height(14); l.flexShrink(0); });
        row.addChild(checkLbl);

        Label idLbl = new Label();
        idLbl.setText(Component.literal(m.id() == null || m.id().isBlank() ? "(未命名模型)" : m.id()));
        idLbl.textStyle(s -> s.textColor(DIALOG_TEXT).textShadow(false).fontSize(10.0F).textWrap(TextWrap.HIDE));
        idLbl.layout(l -> { l.flex(1); l.height(14); });
        row.addChild(idLbl);

        if (m.name() != null && !m.name().isBlank() && !m.name().equals(m.id())) {
            Label nameLbl = new Label();
            nameLbl.setText(Component.literal(m.name()));
            nameLbl.textStyle(s -> s.textColor(DIALOG_SUBTEXT).textShadow(false).fontSize(9.0F));
            nameLbl.layout(l -> { l.width(120); l.height(12); l.flexShrink(0); });
            row.addChild(nameLbl);
        }

        if (m.isDefault()) {
            Label badge = new Label();
            badge.setText(Component.translatable("screen.simukraft.citizen_ai.pick.default"));
            badge.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(false).fontSize(8.0F));
            badge.style(s -> s.backgroundTexture(new ColorRectTexture(DEFAULT_BADGE_BG)));
            badge.layout(l -> { l.width(40); l.height(12); l.flexShrink(0); l.paddingAll(1); });
            row.addChild(badge);
        }
        return row;
    }

    /** resolveEndpointPreferredModel: 优先按端点记忆返回启用模型，否则返回第一个启用模型，都没有返回 null。 */
    private static ClientConfig.AiModel resolveEndpointPreferredModel(ClientConfig.AiEndpoint ep) {
        if (ep == null) return null;
        String lastModelId = ClientConfig.getLastModelByEndpoint(ep.id());
        if (lastModelId != null && !lastModelId.isBlank()) {
            for (ClientConfig.AiModel m : ep.models()) {
                if (m.enabled() && lastModelId.equals(m.id())) {
                    return m;
                }
            }
        }
        for (ClientConfig.AiModel m : ep.models()) {
            if (m.enabled()) {
                return m;
            }
        }
        return null;
    }

    /** resolveDefaultSelection: 优先默认配置，否则第一个启用端点（优先使用其记忆模型）。 */
    private static void resolveDefaultSelection(String[] selectedEpId, String[] selectedModelId) {
        List<ClientConfig.AiEndpoint> eps = ClientConfig.listAiEndpoints();
        String defEp = ClientConfig.CITIZEN_AI_DEFAULT_ENDPOINT_ID.get();
        String defModel = ClientConfig.CITIZEN_AI_DEFAULT_MODEL_ID.get();
        if (defEp != null && !defEp.isBlank() && defModel != null && !defModel.isBlank()) {
            ClientConfig.AiEndpoint ep = findEndpoint(defEp);
            if (ep != null && findModel(ep, defModel) != null) {
                selectedEpId[0] = defEp;
                selectedModelId[0] = defModel;
                return;
            }
        }
        // 回退：第一个启用端点 + 记忆模型（如有效且启用），否则第一个启用模型
        for (ClientConfig.AiEndpoint ep : eps) {
            if (!ep.enabled()) continue;
            ClientConfig.AiModel preferred = resolveEndpointPreferredModel(ep);
            if (preferred != null) {
                selectedEpId[0] = ep.id();
                selectedModelId[0] = preferred.id();
                return;
            }
        }
    }

    private static void refreshStartButton(Button btn, String[] epId, String[] modelId) {
        boolean ok = epId[0] != null && modelId[0] != null;
        btn.setText(Component.translatable("screen.simukraft.citizen_ai.pick.start"));
        btn.style(s -> s.backgroundTexture(new ColorRectTexture(ok ? 0xFF4CAF50 : 0xFF9E9E9E)));
        btn.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(false).fontSize(9.0F));
    }

    private static ClientConfig.AiEndpoint findEndpoint(String id) {
        if (id == null) return null;
        for (ClientConfig.AiEndpoint ep : ClientConfig.listAiEndpoints()) {
            if (id.equals(ep.id())) return ep;
        }
        return null;
    }

    private static ClientConfig.AiModel findModel(ClientConfig.AiEndpoint ep, String modelId) {
        if (ep == null || modelId == null) return null;
        for (ClientConfig.AiModel m : ep.models()) {
            if (modelId.equals(m.id())) return m;
        }
        return null;
    }

    /** smallButton: 小号按钮（文本为翻译 key）。 */
    private static Button smallButton(String key, int width) {
        Button btn = new Button();
        btn.setText(Component.translatable(key));
        btn.style(s -> s.backgroundTexture(new ColorRectTexture(0xFF5D4037)));
        btn.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(false).fontSize(9.0F));
        btn.layout(l -> {
            l.width(width);
            l.height(20);
            l.flexShrink(0);
            l.justifyContent(AlignContent.CENTER);
            l.flexDirection(FlexDirection.ROW);
        });
        return btn;
    }

    /** extractHost: 从 URL 提取可读 host。 */
    private static String extractHost(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return "(无地址)";
        try {
            java.net.URI uri = new java.net.URI(baseUrl.strip());
            String h = uri.getHost();
            if (h != null && !h.isBlank()) return h;
        } catch (Exception ignored) {
        }
        String s = baseUrl.strip();
        if (s.startsWith("https://")) s = s.substring(8);
        else if (s.startsWith("http://")) s = s.substring(7);
        int slash = s.indexOf('/');
        if (slash > 0) s = s.substring(0, slash);
        return s.isEmpty() ? "(无地址)" : s;
    }
}
