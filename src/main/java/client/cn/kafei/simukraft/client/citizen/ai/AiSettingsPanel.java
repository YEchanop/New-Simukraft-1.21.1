package client.cn.kafei.simukraft.client.citizen.ai;

import common.cn.kafei.simukraft.config.ClientConfig;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AiSettingsPanel: AI 设置面板（域名管理 + 模型管理）。
 *
 * <p>提供两个工厂方法：
 * <ul>
 *   <li>{@link #createContent(Runnable, Runnable)} —— 仅对话框内容（不含遮罩）</li>
 *   <li>{@link #createOverlay(Runnable, Runnable)} —— 带遮罩的 overlay</li>
 * </ul>
 * 以及一个便捷工厂 {@link #create(Runnable)} = overlay。
 */
@SuppressWarnings({"null", "DataFlowIssue"})
@OnlyIn(Dist.CLIENT)
public final class AiSettingsPanel {

    private static final int DIALOG_W = 560;
    private static final int DIALOG_H = 420;
    private static final int DIALOG_ACCENT = 0xFF6D4C41;
    private static final int DIALOG_PAPER = 0xFFF5F0E1;
    private static final int DIALOG_TEXT = 0xFF3E2723;
    private static final int DIALOG_SUBTEXT = 0xFF5D4037;
    private static final int OK_GREEN = 0xFF2E7D32;
    private static final int FAIL_RED = 0xFFB71C1C;
    private static final int ENABLED_GREEN_DOT = 0xFF4CAF50;
    private static final int DISABLED_GREY_DOT = 0xFF9E9E9E;

    private AiSettingsPanel() {}

    /** create: 便捷工厂，等价于 createOverlay(() -> {}, onClose)。 */
    public static UIElement create(Runnable onClose) {
        return createOverlay(() -> {}, onClose);
    }

    /** createOverlay: 带遮罩 overlay 的对话框（点击外部不关闭，MOUSE_DOWN 被消费）。 */
    public static UIElement createOverlay(Runnable onSave, Runnable onClose) {
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

        UIElement content = createContent(onSave, onClose);
        overlay.addChild(content);
        overlay.addEventListener(UIEvents.MOUSE_DOWN, e -> e.stopPropagation());
        return overlay;
    }

    /**
     * createContent: 仅 AI 设置面板内容（560x420，不含遮罩层）。
     * 左侧：域名列表；右上：端点编辑；右下：模型列表。
     */
    public static UIElement createContent(Runnable onSave, Runnable onClose) {
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
        title.setText(Component.literal("AI 设置（域名管理 + 模型管理）"));
        title.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(false).fontSize(11.0F));
        title.layout(layout -> { layout.flex(1); layout.height(14); });
        header.addChild(title);

        Button closeBtn = new Button();
        closeBtn.setText(Component.literal("×"));
        closeBtn.setOnClick(e -> onClose.run());
        closeBtn.layout(layout -> { layout.width(20); layout.height(16); layout.flexShrink(0); });
        header.addChild(closeBtn);

        dialog.addChild(header);

        // ===== 主体：左(域名列表) + 右(编辑+模型) =====
        UIElement body = new UIElement().layout(layout -> {
            layout.flex(1);
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(6);
            layout.alignItems(AlignItems.STRETCH);
        });

        // ---------- 左侧：域名列表容器 ----------
        UIElement leftColumn = new UIElement().layout(layout -> {
            layout.width(180);
            layout.flexShrink(0);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(4);
            layout.alignItems(AlignItems.STRETCH);
        });

        Button addEndpointBtn = smallButton("+ 新增端点", 0, () -> {});
        addEndpointBtn.layout(layout -> { layout.widthPercent(100); layout.height(20); });
        leftColumn.addChild(addEndpointBtn);

        UIElement endpointListPanel = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(3);
            layout.paddingAll(3);
        });

        ScrollerView endpointScroller = new ScrollerView();
        endpointScroller.scrollerStyle(s -> s.mode(ScrollerMode.VERTICAL));
        endpointScroller.layout(layout -> { layout.flex(1); layout.widthPercent(100); });
        endpointScroller.addScrollViewChild(endpointListPanel);
        leftColumn.addChild(endpointScroller);

        body.addChild(leftColumn);

        // ---------- 右侧：编辑 + 模型 ----------
        UIElement rightColumn = new UIElement().layout(layout -> {
            layout.flex(1);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(6);
            layout.alignItems(AlignItems.STRETCH);
        });

        // ---- 右上部：端点编辑表单 ----
        UIElement editPanel = new UIElement().layout(layout -> {
            layout.height(200);
            layout.flexShrink(0);
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(3);
            layout.paddingAll(4);
            layout.alignItems(AlignItems.STRETCH);
        }).style(s -> s.backgroundTexture(new ColorRectTexture(0xFFEDE0C8)));

        // 别名行
        UIElement aliasRow = formRow();
        aliasRow.addChild(formLabel("别名"));
        TextField aliasField = new TextField();
        aliasField.setAnyString();
        aliasField.layout(l -> { l.flex(1); l.height(18); });
        aliasRow.addChild(aliasField);
        editPanel.addChild(aliasRow);

        // 基础地址行
        UIElement baseRow = formRow();
        baseRow.addChild(formLabel("基础地址"));
        TextField baseField = new TextField();
        baseField.setAnyString();
        baseField.getTextFieldStyle().placeholder(Component.literal("https://token.sensenova.cn"));
        baseField.layout(l -> { l.flex(1); l.height(18); });
        baseRow.addChild(baseField);
        editPanel.addChild(baseRow);

        // 请求地址预览
        Label previewLabel = new Label();
        previewLabel.setText(Component.literal("请求地址预览："));
        previewLabel.textStyle(s -> s.textColor(DIALOG_SUBTEXT).textShadow(false).fontSize(9.0F));
        previewLabel.layout(l -> { l.widthPercent(100); l.height(10); l.paddingLeft(58); });
        editPanel.addChild(previewLabel);

        // API Key 行 (含眼睛按钮)
        UIElement keyRow = formRow();
        keyRow.addChild(formLabel("API Key"));
        TextField keyField = new TextField();
        keyField.setAnyString();
        keyField.layout(l -> { l.flex(1); l.height(18); });
        keyRow.addChild(keyField);
        Button eyeBtn = new Button();
        eyeBtn.setText(Component.literal("👁"));
        eyeBtn.layout(l -> { l.width(22); l.height(18); l.flexShrink(0); });
        keyRow.addChild(eyeBtn);
        editPanel.addChild(keyRow);

        // 协议行
        UIElement protoRow = formRow();
        protoRow.addChild(formLabel("协议"));
        Label protoLabel = new Label();
        protoLabel.setText(Component.literal("OpenAI 通用（/v1/chat/completions）"));
        protoLabel.textStyle(s -> s.textColor(DIALOG_TEXT).textShadow(false).fontSize(10.0F));
        protoLabel.layout(l -> { l.flex(1); l.height(14); });
        protoRow.addChild(protoLabel);
        editPanel.addChild(protoRow);

        // 启用开关 行
        UIElement enableRow = formRow();
        enableRow.addChild(formLabel("状态"));
        Button enabledToggle = smallButton("启用", 0, () -> {});
        enabledToggle.layout(l -> { l.width(64); l.height(18); l.flexShrink(0); });
        enableRow.addChild(enabledToggle);
        // 填充剩余
        UIElement enableSpacer = new UIElement().layout(l -> { l.flex(1); l.height(18); });
        enableRow.addChild(enableSpacer);
        editPanel.addChild(enableRow);

        // 操作按钮行：保存 / 删除域名 / 测试连通
        UIElement actionRow = new UIElement().layout(l -> {
            l.widthPercent(100);
            l.flexDirection(FlexDirection.ROW);
            l.gapAll(6);
            l.alignItems(AlignItems.CENTER);
            l.paddingTop(2);
        });
        Button saveBtn = smallButton("保存", 72, () -> {});
        saveBtn.layout(l -> { l.height(20); });
        actionRow.addChild(saveBtn);
        Button delBtn = smallButton("删除域名", 72, () -> {});
        delBtn.layout(l -> { l.height(20); });
        actionRow.addChild(delBtn);
        Button testBtn = smallButton("测试连通", 78, () -> {});
        testBtn.layout(l -> { l.height(20); });
        actionRow.addChild(testBtn);
        editPanel.addChild(actionRow);

        rightColumn.addChild(editPanel);

        // ---- 右下部：模型列表 ----
        UIElement modelSection = new UIElement().layout(layout -> {
            layout.flex(1);
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(3);
            layout.alignItems(AlignItems.STRETCH);
        });

        UIElement modelActionRow = new UIElement().layout(l -> {
            l.widthPercent(100);
            l.flexDirection(FlexDirection.ROW);
            l.gapAll(6);
            l.alignItems(AlignItems.CENTER);
            l.height(22);
        });
        Button addModelBtn = smallButton("+ 手动添加模型", 110, () -> {});
        addModelBtn.layout(l -> l.height(18));
        modelActionRow.addChild(addModelBtn);
        Button fetchModelsBtn = smallButton("从/v1/models批量获取", 130, () -> {});
        fetchModelsBtn.layout(l -> l.height(18));
        modelActionRow.addChild(fetchModelsBtn);
        Label fetchStatus = new Label();
        fetchStatus.setText(Component.literal(""));
        fetchStatus.textStyle(s -> s.textColor(DIALOG_SUBTEXT).textShadow(false).fontSize(9.0F));
        fetchStatus.layout(l -> { l.flex(1); l.height(12); });
        modelActionRow.addChild(fetchStatus);
        modelSection.addChild(modelActionRow);

        UIElement modelListPanel = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(3);
            layout.paddingAll(3);
        });

        ScrollerView modelScroller = new ScrollerView();
        modelScroller.scrollerStyle(s -> s.mode(ScrollerMode.VERTICAL));
        modelScroller.layout(layout -> { layout.flex(1); layout.widthPercent(100); });
        modelScroller.addScrollViewChild(modelListPanel);
        modelSection.addChild(modelScroller);

        rightColumn.addChild(modelSection);

        body.addChild(rightColumn);

        dialog.addChild(body);

        // ================================================
        // ===== 状态引用 + 行为绑定（mutable state） =====
        // ================================================
        final String[] selectedEpIdRef = { null };
        final String[] workingRealApiKey = { "" };
        final boolean[] keyMaskedRef = { false };
        final boolean[] keyProgrammaticUpdate = { false };
        final boolean[] enabledStateRef = { true };
        // workingModelsRef holds the *editable* list for the currently selected endpoint
        // Each entry: Object[4] = {String id, String name, boolean enabled[1-element array], boolean isDefault}
        @SuppressWarnings("unchecked")
        final List<Object[]>[] workingModelsRef = new List[]{ new ArrayList<>() };
        final String[] defaultModelIdRef = { "" };

        // ====== 内部：刷新预览标签 ======
        Runnable updatePreview = () -> {
            String base = baseField.getValue() == null ? "" : baseField.getValue().strip();
            String safe = base;
            while (!safe.isEmpty() && safe.charAt(safe.length() - 1) == '/') {
                safe = safe.substring(0, safe.length() - 1);
            }
            String url = safe + "/v1/chat/completions";
            previewLabel.setText(Component.literal("请求地址预览：" + url));
        };
        baseField.setTextResponder(t -> updatePreview.run());

        // ====== 内部：刷新启用按钮文案 ======
        Runnable refreshEnabledToggleText = () -> {
            if (enabledStateRef[0]) {
                enabledToggle.setText(Component.literal("✅ 启用中"));
                enabledToggle.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(false).fontSize(9.0F));
                enabledToggle.style(s -> s.backgroundTexture(new ColorRectTexture(0xFF4CAF50)));
            } else {
                enabledToggle.setText(Component.literal("⛔ 已停用"));
                enabledToggle.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(false).fontSize(9.0F));
                enabledToggle.style(s -> s.backgroundTexture(new ColorRectTexture(0xFF757575)));
            }
        };
        enabledToggle.setOnClick(e -> {
            enabledStateRef[0] = !enabledStateRef[0];
            refreshEnabledToggleText.run();
        });

        // ====== 内部：眼睛按钮（密文/明文切换） ======
        Runnable applyKeyFieldDisplay = () -> {
            keyProgrammaticUpdate[0] = true;
            if (keyMaskedRef[0] && !workingRealApiKey[0].isEmpty()) {
                keyField.setText("********");
            } else {
                keyField.setText(workingRealApiKey[0]);
            }
            keyProgrammaticUpdate[0] = false;
        };
        eyeBtn.setOnClick(e -> {
            keyMaskedRef[0] = !keyMaskedRef[0];
            applyKeyFieldDisplay.run();
        });
        keyField.setTextResponder(t -> {
            if (keyProgrammaticUpdate[0]) return;
            // 用户主动输入 → 更新真实值 + 切到明文模式
            if (keyMaskedRef[0]) {
                // 如果之前是 8 个星的显示状态，用户打字了，切换到明文编辑
                keyMaskedRef[0] = false;
            }
            workingRealApiKey[0] = t == null ? "" : t;
        });

        // ====== 内部：重建左侧端点列表 ======
        Runnable rebuildEndpointList = () -> {
            endpointListPanel.clearAllChildren();
            List<ClientConfig.AiEndpoint> eps = ClientConfig.listAiEndpoints();
            if (eps.isEmpty()) {
                Label empty = new Label();
                empty.setText(Component.literal("（暂无端点，点击上方新增）"));
                empty.textStyle(s -> s.textColor(DIALOG_SUBTEXT).textShadow(false).fontSize(9.0F));
                empty.layout(l -> { l.widthPercent(100); l.height(12); });
                endpointListPanel.addChild(empty);
                return;
            }
            for (ClientConfig.AiEndpoint ep : eps) {
                boolean selected = ep.id().equals(selectedEpIdRef[0]);
                endpointListPanel.addChild(endpointRow(ep, selected, () -> {
                    // 选中回调：装载到右侧编辑区
                    loadEndpointToEditor(
                            ep, selectedEpIdRef, aliasField, baseField,
                            workingRealApiKey, keyMaskedRef, applyKeyFieldDisplay,
                            enabledStateRef, refreshEnabledToggleText,
                            workingModelsRef, defaultModelIdRef, updatePreview);
                }));
            }
        };

        // ====== 内部：重建右侧模型列表 ======
        // 用 holder 数组避免 lambda 初始化前自引用（modelEditorRow 回调里引用自身）
        final Runnable[] rebuildModelListRef = new Runnable[1];
        rebuildModelListRef[0] = () -> {
            modelListPanel.clearAllChildren();
            List<Object[]> models = workingModelsRef[0];
            if (models.isEmpty()) {
                Label empty = new Label();
                empty.setText(Component.literal("（暂无模型，点击上方新增或批量获取）"));
                empty.textStyle(s -> s.textColor(DIALOG_SUBTEXT).textShadow(false).fontSize(9.0F));
                empty.layout(l -> { l.widthPercent(100); l.height(12); });
                modelListPanel.addChild(empty);
                return;
            }
            for (int i = 0; i < models.size(); i++) {
                final int idx = i;
                Object[] row = models.get(i);
                modelListPanel.addChild(modelEditorRow(
                        idx, row, defaultModelIdRef,
                        workingModelsRef,
                        () -> rebuildModelListRef[0].run()
                ));
            }
        };

        // ====== 内部：从编辑区构造 AiEndpoint（用于保存）======
        java.util.function.Supplier<ClientConfig.AiEndpoint> collectEndpointFromEditor = () -> {
            String id = selectedEpIdRef[0];
            if (id == null || id.isBlank()) {
                id = UUID.randomUUID().toString();
                selectedEpIdRef[0] = id;
            }
            String alias = aliasField.getValue() == null ? "" : aliasField.getValue().trim();
            String base = baseField.getValue() == null ? "" : baseField.getValue().trim();
            String apiKey = workingRealApiKey[0] == null ? "" : workingRealApiKey[0];
            boolean enabled = enabledStateRef[0];
            String localDefaultModelId = defaultModelIdRef[0];

            List<ClientConfig.AiModel> models = new ArrayList<>();
            for (Object[] r : workingModelsRef[0]) {
                String mid = (String) r[0];
                String mname = (String) r[1];
                boolean menabled = ((boolean[]) r[2])[0];
                boolean mdef = localDefaultModelId != null && localDefaultModelId.equals(mid);
                if (mid == null || mid.isBlank()) continue;
                models.add(new ClientConfig.AiModel(mid, mname, menabled, mdef));
            }

            return new ClientConfig.AiEndpoint(id, alias, base, apiKey, "openai", enabled, models);
        };

        // ====== 新增端点按钮 ======
        addEndpointBtn.setOnClick(e -> {
            selectedEpIdRef[0] = UUID.randomUUID().toString();
            aliasField.setValue("");
            baseField.setValue("");
            workingRealApiKey[0] = "";
            keyMaskedRef[0] = false;
            applyKeyFieldDisplay.run();
            enabledStateRef[0] = true;
            refreshEnabledToggleText.run();
            workingModelsRef[0] = new ArrayList<>();
            defaultModelIdRef[0] = "";
            updatePreview.run();
            rebuildEndpointList.run();
            rebuildModelListRef[0].run();
            // reset test/fetch status
            testBtn.setText(Component.literal("测试连通"));
            testBtn.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(false).fontSize(9.0F));
            testBtn.style(s -> s.backgroundTexture(new ColorRectTexture(0xFF5D4037)));
            fetchStatus.setText(Component.literal(""));
        });

        // ====== 保存按钮 ======
        saveBtn.setOnClick(e -> {
            ClientConfig.AiEndpoint ep = collectEndpointFromEditor.get();
            ClientConfig.addAiEndpoint(ep);
            if (onSave != null) onSave.run();
            rebuildEndpointList.run();
            // After save, reload to ensure consistency
            ClientConfig.AiEndpoint reloaded = null;
            for (ClientConfig.AiEndpoint x : ClientConfig.listAiEndpoints()) {
                if (x.id().equals(ep.id())) { reloaded = x; break; }
            }
            if (reloaded != null) {
                loadEndpointToEditor(
                        reloaded, selectedEpIdRef, aliasField, baseField,
                        workingRealApiKey, keyMaskedRef, applyKeyFieldDisplay,
                        enabledStateRef, refreshEnabledToggleText,
                        workingModelsRef, defaultModelIdRef, updatePreview);
            }
        });

        // ====== 删除域名按钮 ======
        delBtn.setOnClick(e -> {
            String id = selectedEpIdRef[0];
            if (id == null || id.isBlank()) return;
            ClientConfig.removeAiEndpoint(id);
            selectedEpIdRef[0] = null;
            aliasField.setValue("");
            baseField.setValue("");
            workingRealApiKey[0] = "";
            keyMaskedRef[0] = false;
            applyKeyFieldDisplay.run();
            enabledStateRef[0] = true;
            refreshEnabledToggleText.run();
            workingModelsRef[0] = new ArrayList<>();
            defaultModelIdRef[0] = "";
            updatePreview.run();
            rebuildEndpointList.run();
            rebuildModelListRef[0].run();
            if (onSave != null) onSave.run();
        });

        // ====== 测试连通按钮 ======
        testBtn.style(s -> s.backgroundTexture(new ColorRectTexture(0xFF5D4037)));
        testBtn.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(false).fontSize(9.0F));
        testBtn.setOnClick(e -> {
            ClientConfig.AiEndpoint ep = collectEndpointFromEditor.get();
            if (ep.baseUrl() == null || ep.baseUrl().isBlank()) {
                testBtn.setText(Component.literal("连通:请填地址"));
                testBtn.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(false).fontSize(9.0F));
                testBtn.style(s -> s.backgroundTexture(new ColorRectTexture(FAIL_RED)));
                return;
            }
            testBtn.setText(Component.literal("测试中..."));
            testBtn.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(false).fontSize(9.0F));
            testBtn.style(s -> s.backgroundTexture(new ColorRectTexture(0xFF795548)));
            CitizenAiChatService.instance().testModelsEndpointDetail(ep).thenAccept(detail -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc == null) return;
                mc.execute(() -> {
                    if (detail.success()) {
                        testBtn.setText(Component.literal("连通:OK"));
                        testBtn.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(false).fontSize(9.0F));
                        testBtn.style(s -> s.backgroundTexture(new ColorRectTexture(OK_GREEN)));
                    } else {
                        String msg = detail.message();
                        if (msg.length() > 14) msg = msg.substring(0, 14) + "..";
                        testBtn.setText(Component.literal("连通:失败 " + msg));
                        testBtn.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(false).fontSize(9.0F));
                        testBtn.style(s -> s.backgroundTexture(new ColorRectTexture(FAIL_RED)));
                    }
                });
            });
        });

        // ====== + 手动添加模型 ======
        addModelBtn.setOnClick(e -> {
            Object[] newRow = new Object[]{
                    "",                // id
                    "新模型",           // name
                    new boolean[]{true},  // enabled
                    false              // dummy isDefault flag (not used directly, uses defaultModelIdRef)
            };
            workingModelsRef[0].add(newRow);
            rebuildModelListRef[0].run();
        });

        // ====== 从 /v1/models 批量获取 ======
        fetchModelsBtn.setOnClick(e -> {
            ClientConfig.AiEndpoint ep = collectEndpointFromEditor.get();
            if (ep.baseUrl() == null || ep.baseUrl().isBlank()) {
                fetchStatus.setText(Component.literal("请先填写基础地址"));
                fetchStatus.textStyle(s -> s.textColor(FAIL_RED).textShadow(false).fontSize(9.0F));
                return;
            }
            fetchStatus.setText(Component.literal("获取中..."));
            fetchStatus.textStyle(s -> s.textColor(DIALOG_SUBTEXT).textShadow(false).fontSize(9.0F));
            CitizenAiChatService.instance().fetchModelIds(ep).thenAccept(ids -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc == null) return;
                mc.execute(() -> {
                    if (ids == null || ids.isEmpty()) {
                        fetchStatus.setText(Component.literal("获取失败或无模型"));
                        fetchStatus.textStyle(s -> s.textColor(FAIL_RED).textShadow(false).fontSize(9.0F));
                        return;
                    }
                    // 合并到 workingModels（按 id 去重）
                    List<Object[]> existing = workingModelsRef[0];
                    java.util.Set<String> have = new java.util.HashSet<>();
                    for (Object[] r : existing) {
                        String id = (String) r[0];
                        if (id != null) have.add(id);
                    }
                    int added = 0;
                    for (String id : ids) {
                        if (have.contains(id)) continue;
                        existing.add(new Object[]{id, id, new boolean[]{true}, false});
                        have.add(id);
                        added++;
                    }
                    fetchStatus.setText(Component.literal("新增 " + added + " 个模型（共" + ids.size() + "个）"));
                    fetchStatus.textStyle(s -> s.textColor(OK_GREEN).textShadow(false).fontSize(9.0F));
                    rebuildModelListRef[0].run();
                });
            });
        });

        // ======================================================
        // ===== 初始渲染：若无选中则默认选第一个启用端点 ========
        // ======================================================
        rebuildEndpointList.run();
        refreshEnabledToggleText.run();
        // 默认选中第一个端点（如果有）
        List<ClientConfig.AiEndpoint> initial = ClientConfig.listAiEndpoints();
        if (!initial.isEmpty()) {
            ClientConfig.AiEndpoint first = initial.get(0);
            for (ClientConfig.AiEndpoint ep : initial) {
                if (ep.enabled()) { first = ep; break; }
            }
            loadEndpointToEditor(
                    first, selectedEpIdRef, aliasField, baseField,
                    workingRealApiKey, keyMaskedRef, applyKeyFieldDisplay,
                    enabledStateRef, refreshEnabledToggleText,
                    workingModelsRef, defaultModelIdRef, updatePreview);
            rebuildEndpointList.run();
        }
        rebuildModelListRef[0].run();
        updatePreview.run();

        return dialog;
    }

    // ======================================================================
    // 内部辅助：UI 构建小工具
    // ======================================================================

    /** smallButton：小号按钮（固定文本非翻译 key）。 */
    private static Button smallButton(String text, int width, Runnable onClick) {
        Button btn = new Button();
        btn.setText(Component.literal(text));
        btn.setOnClick(e -> onClick.run());
        btn.style(s -> s.backgroundTexture(new ColorRectTexture(0xFF5D4037)));
        btn.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(false).fontSize(9.0F));
        if (width > 0) {
            btn.layout(l -> { l.width(width); l.height(18); l.flexShrink(0); l.justifyContent(AlignContent.CENTER); l.flexDirection(FlexDirection.ROW); });
        } else {
            btn.layout(l -> { l.height(18); l.flexShrink(0); l.justifyContent(AlignContent.CENTER); l.flexDirection(FlexDirection.ROW); });
        }
        return btn;
    }

    /** formRow：表单两列行（label + field）。 */
    private static UIElement formRow() {
        return new UIElement().layout(l -> {
            l.widthPercent(100);
            l.flexDirection(FlexDirection.ROW);
            l.gapAll(4);
            l.alignItems(AlignItems.CENTER);
            l.height(20);
        });
    }

    /** formLabel：表单左侧固定宽度标签。 */
    private static Label formLabel(String text) {
        Label lbl = new Label();
        lbl.setText(Component.literal(text));
        lbl.textStyle(s -> s.textColor(DIALOG_TEXT).textShadow(false).fontSize(10.0F));
        lbl.layout(l -> { l.width(52); l.height(12); l.flexShrink(0); });
        return lbl;
    }

    /** endpointRow：左侧端点列表单行。 */
    private static UIElement endpointRow(ClientConfig.AiEndpoint ep, boolean selected, Runnable onSelect) {
        UIElement row = new UIElement().layout(l -> {
            l.widthPercent(100);
            l.height(36);
            l.flexDirection(FlexDirection.ROW);
            l.gapAll(4);
            l.alignItems(AlignItems.CENTER);
            l.paddingAll(3);
        });
        row.style(s -> s.backgroundTexture(new ColorRectTexture(selected ? 0xFFD7CCC8 : 0x00000000)));

        // 启用状态小圆点
        UIElement dot = new UIElement().layout(l -> { l.width(8); l.height(8); l.flexShrink(0); });
        dot.style(s -> s.backgroundTexture(new ColorRectTexture(ep.enabled() ? ENABLED_GREEN_DOT : DISABLED_GREY_DOT)));
        row.addChild(dot);

        UIElement texts = new UIElement().layout(l -> {
            l.flex(1);
            l.flexDirection(FlexDirection.COLUMN);
            l.gapAll(1);
        });
        Label alias = new Label();
        String aliasTxt = (ep.alias() == null || ep.alias().isBlank()) ? "(未命名)" : ep.alias();
        alias.setText(Component.literal(aliasTxt));
        alias.textStyle(s -> s.textColor(DIALOG_TEXT).textShadow(false).fontSize(10.0F));
        alias.layout(l -> { l.widthPercent(100); l.height(12); });
        texts.addChild(alias);

        Label hostLbl = new Label();
        hostLbl.setText(Component.literal(extractHost(ep.baseUrl())));
        hostLbl.textStyle(s -> s.textColor(DIALOG_SUBTEXT).textShadow(false).fontSize(8.5F));
        hostLbl.layout(l -> { l.widthPercent(100); l.height(10); });
        texts.addChild(hostLbl);
        row.addChild(texts);

        row.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) onSelect.run();
        });
        return row;
    }

    /** extractHost：从 URL 提取可读 host（去掉 https:// 和路径）。 */
    private static String extractHost(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return "(无地址)";
        try {
            URI uri = new URI(baseUrl.strip());
            String h = uri.getHost();
            if (h != null && !h.isBlank()) return h;
        } catch (Exception ignored) {}
        // fallback: 手动裁剪
        String s = baseUrl.strip();
        if (s.startsWith("https://")) s = s.substring(8);
        else if (s.startsWith("http://")) s = s.substring(7);
        int slash = s.indexOf('/');
        if (slash > 0) s = s.substring(0, slash);
        int q = s.indexOf('?');
        if (q > 0) s = s.substring(0, q);
        return s.isEmpty() ? "(无地址)" : s;
    }

    /** loadEndpointToEditor：把指定端点数据装载到右侧编辑器。 */
    private static void loadEndpointToEditor(
            ClientConfig.AiEndpoint ep,
            String[] selectedEpIdRef,
            TextField aliasField, TextField baseField,
            String[] workingRealApiKey, boolean[] keyMaskedRef,
            Runnable applyKeyFieldDisplay,
            boolean[] enabledStateRef, Runnable refreshEnabledToggleText,
            List<Object[]>[] workingModelsRef, String[] defaultModelIdRef,
            Runnable updatePreview) {
        selectedEpIdRef[0] = ep.id();
        aliasField.setValue(ep.alias() == null ? "" : ep.alias());
        baseField.setValue(ep.baseUrl() == null ? "" : ep.baseUrl());
        workingRealApiKey[0] = ep.apiKey() == null ? "" : ep.apiKey();
        // 有 key → 默认加密显示；空 key → 明文（空）显示
        keyMaskedRef[0] = !workingRealApiKey[0].isEmpty();
        applyKeyFieldDisplay.run();
        enabledStateRef[0] = ep.enabled();
        refreshEnabledToggleText.run();
        // 模型工作副本
        List<Object[]> copy = new ArrayList<>();
        String defId = "";
        List<ClientConfig.AiModel> src = ep.models();
        if (src != null) {
            for (ClientConfig.AiModel m : src) {
                copy.add(new Object[]{
                        m.id() == null ? "" : m.id(),
                        m.name() == null ? "" : m.name(),
                        new boolean[]{m.enabled()},
                        m.isDefault()
                });
                if (m.isDefault()) defId = m.id() == null ? "" : m.id();
            }
        }
        workingModelsRef[0] = copy;
        defaultModelIdRef[0] = defId;
        updatePreview.run();
    }

    /** modelEditorRow：模型列表单条编辑器行。 */
    private static UIElement modelEditorRow(
            int idx, Object[] rowData,
            String[] defaultModelIdRef,
            List<Object[]>[] workingModelsRef,
            Runnable rebuildModelList) {
        UIElement row = new UIElement().layout(l -> {
            l.widthPercent(100);
            l.height(24);
            l.flexDirection(FlexDirection.ROW);
            l.gapAll(3);
            l.alignItems(AlignItems.CENTER);
            l.paddingAll(2);
        });

        String rowId = (String) rowData[0];
        boolean isDefault = defaultModelIdRef[0] != null && defaultModelIdRef[0].equals(rowId);

        // modelId textField
        TextField idField = new TextField();
        idField.setAnyString();
        idField.setValue(rowId);
        idField.layout(l -> { l.width(120); l.height(20); l.flexShrink(0); });
        idField.getTextFieldStyle().placeholder(Component.literal("model_id"));
        idField.setTextResponder(t -> { rowData[0] = t == null ? "" : t; });
        row.addChild(idField);

        // name textField
        TextField nameField = new TextField();
        nameField.setAnyString();
        nameField.setValue((String) rowData[1]);
        nameField.layout(l -> { l.flex(1); l.height(20); });
        nameField.getTextFieldStyle().placeholder(Component.literal("显示名"));
        nameField.setTextResponder(t -> { rowData[1] = t == null ? "" : t; });
        row.addChild(nameField);

        // 启用 toggle
        boolean[] enabledArr = (boolean[]) rowData[2];
        Button enableBtn = new Button();
        enableBtn.layout(l -> { l.width(38); l.height(18); l.flexShrink(0); l.justifyContent(AlignContent.CENTER); l.flexDirection(FlexDirection.ROW); });
        Runnable refreshEnableBtn = () -> {
            if (enabledArr[0]) {
                enableBtn.setText(Component.literal("开"));
                enableBtn.style(s -> s.backgroundTexture(new ColorRectTexture(0xFF4CAF50)));
            } else {
                enableBtn.setText(Component.literal("关"));
                enableBtn.style(s -> s.backgroundTexture(new ColorRectTexture(0xFF9E9E9E)));
            }
            enableBtn.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(false).fontSize(9.0F));
        };
        refreshEnableBtn.run();
        enableBtn.setOnClick(e -> {
            enabledArr[0] = !enabledArr[0];
            refreshEnableBtn.run();
        });
        row.addChild(enableBtn);

        // 设默认
        Button defaultBtn = new Button();
        defaultBtn.layout(l -> { l.width(46); l.height(18); l.flexShrink(0); l.justifyContent(AlignContent.CENTER); l.flexDirection(FlexDirection.ROW); });
        Runnable refreshDefaultBtn = () -> {
            String currentId = (String) rowData[0];
            boolean nowDefault = currentId != null && currentId.equals(defaultModelIdRef[0]);
            if (nowDefault) {
                defaultBtn.setText(Component.literal("★默认"));
                defaultBtn.style(s -> s.backgroundTexture(new ColorRectTexture(0xFFE65100)));
            } else {
                defaultBtn.setText(Component.literal("设默认"));
                defaultBtn.style(s -> s.backgroundTexture(new ColorRectTexture(0xFF795548)));
            }
            defaultBtn.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(false).fontSize(9.0F));
        };
        refreshDefaultBtn.run();
        defaultBtn.setOnClick(e -> {
            String currentId = (String) rowData[0];
            if (currentId == null || currentId.isBlank()) return;
            defaultModelIdRef[0] = currentId;
            // 不立即写盘；保存端点时一起写。此处只刷新每行按钮显示。
            rebuildModelList.run();
        });
        row.addChild(defaultBtn);

        // 删除
        Button delBtn = new Button();
        delBtn.setText(Component.literal("删"));
        delBtn.style(s -> s.backgroundTexture(new ColorRectTexture(FAIL_RED)));
        delBtn.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(false).fontSize(9.0F));
        delBtn.layout(l -> { l.width(28); l.height(18); l.flexShrink(0); l.justifyContent(AlignContent.CENTER); l.flexDirection(FlexDirection.ROW); });
        delBtn.setOnClick(e -> {
            if (idx < workingModelsRef[0].size()) {
                workingModelsRef[0].remove(idx);
                // 若被删的是默认模型，清空默认
                String removedId = (String) rowData[0];
                if (removedId != null && removedId.equals(defaultModelIdRef[0])) {
                    defaultModelIdRef[0] = "";
                }
                rebuildModelList.run();
            }
        });
        row.addChild(delBtn);

        // 初次默认状态时高亮这一行背景
        if (isDefault) {
            row.style(s -> s.backgroundTexture(new ColorRectTexture(0xFFFFF3E0)));
        }

        return row;
    }
}
