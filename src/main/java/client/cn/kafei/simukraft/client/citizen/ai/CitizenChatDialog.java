package client.cn.kafei.simukraft.client.citizen.ai;

import common.cn.kafei.simukraft.config.ClientConfig;
import common.cn.kafei.simukraft.network.citizen.chat.CitizenChatContextRequestPacket;
import common.cn.kafei.simukraft.network.citizen.chat.CitizenChatContextResponsePacket;
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
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * CitizenChatDialog: 市民 AI 聊天对话框（Task 6）。
 *
 * <p>生命周期：
 * <ol>
 *   <li>打开时立即向服务端请求 {@link CitizenChatContextResponsePacket} 上下文快照；</li>
 *   <li>若尚未选择模型（首次打开或切换模型前），先弹出内置 {@link AiModelPickerDialog}；</li>
 *   <li>上下文到达 + 模型选定后创建 {@link CitizenAiChatService.ChatSession} 并进入聊天界面；</li>
 *   <li>「切换模型」重开选择器，确认后重建 session（保留 history，system prompt 不变）；</li>
 *   <li>关闭按钮仅关闭本弹窗，不发送任何包。</li>
 * </ol>
 *
 * <p>输入：Enter 发送（Shift+Enter 不触发发送，交由 TextField 默认处理）。
 */
@SuppressWarnings({"null", "DataFlowIssue"})
@OnlyIn(Dist.CLIENT)
public final class CitizenChatDialog {

    private static final int DIALOG_W = 560;
    private static final int DIALOG_H = 440;
    private static final int DIALOG_ACCENT = 0xFF6D4C41;
    private static final int DIALOG_PAPER = 0xFFF5F0E1;
    private static final int DIALOG_TEXT = 0xFF3E2723;
    private static final int DIALOG_SUBTEXT = 0xFF5D4037;
    private static final int PLAYER_BUBBLE = 0xFFD7CCC8;
    private static final int CITIZEN_BUBBLE = 0xFFEDE0C8;
    private static final int ERROR_RED = 0xFFB71C1C;

    private CitizenChatDialog() {
    }

    /**
     * create: 创建带遮罩的聊天对话框 overlay。
     *
     * @param cityId        城市 UUID（权限二次校验用，可为 null）
     * @param citizenId     目标市民 UUID
     * @param citizenName   市民显示名（顶栏与气泡标签用）
     * @param corePos       城市核心坐标
     * @param onClose       关闭回调（由外层隐藏 holder）
     * @param onOpenSettings 打开 AI 设置面板回调（模型选择器齿轮）
     */
    public static UIElement create(UUID cityId, UUID citizenId, String citizenName, BlockPos corePos,
                                   Runnable onClose, Runnable onOpenSettings) {
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
            layout.gapAll(4);
            layout.paddingAll(4);
        }).style(style -> style.backgroundTexture(new ColorRectTexture(DIALOG_ACCENT)));

        Label title = new Label();
        title.setText(Component.translatable("screen.simukraft.citizen_ai.chat.title",
                Component.literal(citizenName == null ? "" : citizenName)));
        title.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(false).fontSize(11.0F).textWrap(TextWrap.HIDE));
        title.layout(layout -> { layout.flex(1); layout.height(14); });
        header.addChild(title);

        Button regenBtn = headerButton("screen.simukraft.citizen_ai.chat.regenerate", 52);
        Button clearBtn = headerButton("screen.simukraft.citizen_ai.chat.clear", 44);
        Button switchBtn = headerButton("screen.simukraft.citizen_ai.chat.switch_model", 56);
        Button closeBtn = headerButton("screen.simukraft.citizen_ai.chat.close", 32);
        closeBtn.setText(Component.literal("×"));
        header.addChild(regenBtn);
        header.addChild(clearBtn);
        header.addChild(switchBtn);
        header.addChild(closeBtn);
        dialog.addChild(header);

        // ===== 顶栏摘要（上下文加载后填充） =====
        Label summaryLabel = new Label();
        summaryLabel.setText(Component.translatable("screen.simukraft.citizen_ai.chat.loading_context"));
        summaryLabel.textStyle(s -> s.textColor(DIALOG_SUBTEXT).textShadow(false).fontSize(9.0F).textWrap(TextWrap.WRAP));
        summaryLabel.layout(layout -> { layout.widthPercent(100); layout.height(26); });
        dialog.addChild(summaryLabel);

        // ===== 聊天区（滚动） =====
        UIElement msgPanel = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(4);
            layout.paddingAll(3);
        });
        ScrollerView scroller = new ScrollerView();
        scroller.scrollerStyle(s -> s.mode(ScrollerMode.VERTICAL));
        scroller.layout(layout -> { layout.flex(1); layout.widthPercent(100); });
        scroller.addScrollViewChild(msgPanel);
        dialog.addChild(scroller);

        // ===== Loading 指示 =====
        Label loadingLabel = new Label();
        loadingLabel.setText(Component.translatable("screen.simukraft.citizen_ai.chat.typing"));
        loadingLabel.textStyle(s -> s.textColor(DIALOG_SUBTEXT).textShadow(false).fontSize(9.0F));
        loadingLabel.layout(layout -> { layout.widthPercent(100); layout.height(12); });
        loadingLabel.setVisible(false);
        dialog.addChild(loadingLabel);

        // ===== 输入区 =====
        UIElement inputRow = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(6);
            layout.alignItems(AlignItems.CENTER);
            layout.height(26);
        });
        TextField inputField = new TextField();
        inputField.setAnyString();
        inputField.getTextFieldStyle().placeholder(Component.translatable("screen.simukraft.citizen_ai.chat.input_placeholder"));
        inputField.layout(layout -> { layout.flex(1); layout.height(22); });
        inputRow.addChild(inputField);
        Button sendBtn = bottomButton("screen.simukraft.citizen_ai.chat.send", 64);
        Button stopBtn = bottomButton("screen.simukraft.citizen_ai.chat.stop", 64);
        stopBtn.setVisible(false);
        inputRow.addChild(sendBtn);
        inputRow.addChild(stopBtn);
        dialog.addChild(inputRow);

        overlay.addChild(dialog);

        // ===== 内部状态 =====
        final CitizenChatContextResponsePacket[] ctxRef = {null};
        final ClientConfig.AiEndpoint[] epRef = {null};
        final ClientConfig.AiModel[] modelRef = {null};
        final CitizenAiChatService.ChatSession[] sessionRef = {null};
        final CompletableFuture<?>[] inFlight = {null};

        // 内置模型选择器（子 overlay）
        final UIElement[] pickerRef = {null};
        pickerRef[0] = AiModelPickerDialog.create(
                () -> { if (pickerRef[0] != null) pickerRef[0].setVisible(false); },
                (ep, model) -> {
                    epRef[0] = ep;
                    modelRef[0] = model;
                    if (pickerRef[0] != null) pickerRef[0].setVisible(false);
                    if (ctxRef[0] != null) {
                        // 切换模型：重建 session 并保留对话历史。createSession 内部会从缓存 loadHistory，
                        // 但缓存可能比当前 session 旧（对话后未 send 保存），故先把当前最新历史写缓存，
                        // 再 createSession 读取，保证逻辑统一且不重复追加。
                        if (sessionRef[0] != null && epRef[0] != null) {
                            CitizenAiChatService.saveHistory(citizenId, epRef[0].baseUrl(), sessionRef[0].history);
                        }
                        createSession(sessionRef, epRef, modelRef, citizenId, ctxRef[0]);
                    }
                    refreshSummary(summaryLabel, citizenName, epRef, modelRef, ctxRef);
                    rebuildMessages(msgPanel, sessionRef, citizenName);
                },
                onOpenSettings);
        pickerRef[0].setVisible(false);
        dialog.addChild(pickerRef[0]);

        // ===== 关闭按钮 =====
        closeBtn.setOnClick(e -> onClose.run());

        // ===== 重新生成：重放最后一条 user =====
        regenBtn.setOnClick(e -> {
            CitizenAiChatService.ChatSession s = sessionRef[0];
            if (s == null || inFlight[0] != null) return;
            String last = s.lastUser();
            if (last == null || last.isEmpty()) return;
            // 移除最后一条 user 及紧随的 assistant（若存在），保留 system 及之前历史
            for (int i = s.history.size() - 1; i >= 1; i--) {
                if ("user".equals(s.history.get(i).role)) {
                    s.history.remove(i);
                    if (s.history.size() > 1 && "assistant".equals(s.history.get(s.history.size() - 1).role)) {
                        s.history.remove(s.history.size() - 1);
                    }
                    break;
                }
            }
            // 同步缓存：重新生成会重放该 user，缓存需反映移除后的历史
            CitizenAiChatService.saveHistory(s.citizenId, s.endpoint.baseUrl(), s.history);
            rebuildMessages(msgPanel, sessionRef, citizenName);
            send(sessionRef, inFlight, inputField, loadingLabel, stopBtn, sendBtn,
                    msgPanel, citizenName, last);
        });

        // ===== 清空上下文 =====
        clearBtn.setOnClick(e -> {
            CitizenAiChatService.ChatSession s = sessionRef[0];
            if (s != null) {
                s.clearHistoryPairs();
                CitizenAiChatService.clearHistory(s.citizenId, s.endpoint.baseUrl());
            }
            rebuildMessages(msgPanel, sessionRef, citizenName);
        });

        // ===== 切换模型 =====
        switchBtn.setOnClick(e -> {
            if (pickerRef[0] != null) pickerRef[0].setVisible(true);
        });

        // ===== 发送 =====
        Runnable doSend = () -> {
            if (sessionRef[0] == null || inFlight[0] != null) return;
            String text = inputField.getValue();
            if (text == null || text.trim().isEmpty()) return;
            inputField.setValue("");
            send(sessionRef, inFlight, inputField, loadingLabel, stopBtn, sendBtn,
                    msgPanel, citizenName, text);
        };
        sendBtn.setOnClick(e -> doSend.run());
        stopBtn.setOnClick(e -> {
            if (inFlight[0] != null) {
                inFlight[0].cancel(true);
                inFlight[0] = null;
                loadingLabel.setVisible(false);
                stopBtn.setVisible(false);
                sendBtn.setVisible(true);
            }
        });
        // Enter 发送（Shift+Enter 不触发）
        dialog.addEventListener(UIEvents.KEY_DOWN, e -> {
            if (e.keyCode == GLFW.GLFW_KEY_ENTER && (e.modifiers & GLFW.GLFW_MOD_SHIFT) == 0) {
                doSend.run();
            }
        });

        // ===== 请求上下文（打开时立即发送） =====
        CompletableFuture<CitizenChatContextResponsePacket> future =
                CitizenChatContextResponsePacket.requestFuture(citizenId, 10_000L);
        PacketDistributor.sendToServer(new CitizenChatContextRequestPacket(cityId, citizenId, corePos));
        future.whenComplete((resp, err) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            mc.execute(() -> {
                if (err != null || resp == null) {
                    summaryLabel.setText(Component.translatable("screen.simukraft.citizen_ai.chat.context_timeout"));
                    summaryLabel.textStyle(s -> s.textColor(ERROR_RED).textShadow(false).fontSize(9.0F));
                    return;
                }
                ctxRef[0] = resp;
                if (resp.errorCode() != 0) {
                    summaryLabel.setText(Component.translatable(ctxErrorKey(resp.errorCode())));
                    summaryLabel.textStyle(s -> s.textColor(ERROR_RED).textShadow(false).fontSize(9.0F));
                    return;
                }
                // 若模型已选定，创建 session 并进入聊天
                if (epRef[0] != null && modelRef[0] != null && sessionRef[0] == null) {
                    createSession(sessionRef, epRef, modelRef, citizenId, resp);
                }
                refreshSummary(summaryLabel, citizenName, epRef, modelRef, ctxRef);
                rebuildMessages(msgPanel, sessionRef, citizenName);
                // 首次无模型 → 弹出模型选择器
                if (epRef[0] == null || modelRef[0] == null) {
                    if (pickerRef[0] != null) pickerRef[0].setVisible(true);
                }
            });
        });

        return overlay;
    }

    // ======================================================================
    // 内部辅助
    // ======================================================================

    private static void createSession(CitizenAiChatService.ChatSession[] sessionRef,
                                      ClientConfig.AiEndpoint[] epRef,
                                      ClientConfig.AiModel[] modelRef,
                                      UUID citizenId,
                                      CitizenChatContextResponsePacket ctx) {
        if (epRef[0] == null || modelRef[0] == null) return;
        CitizenAiChatService.ChatSession newSession =
                new CitizenAiChatService.ChatSession(citizenId, epRef[0], modelRef[0], ctx);
        // 跨对话框复用：同一市民 + 同一网站的缓存历史（非 system 消息）追加到新 session。
        // system prompt 用最新 ctx 重建，历史对话对保留，避免重复打开丢失上次聊天记录。
        java.util.List<CitizenAiChatService.ChatMessage> cached =
                CitizenAiChatService.loadHistory(citizenId, epRef[0].baseUrl());
        if (cached != null) {
            for (CitizenAiChatService.ChatMessage m : cached) {
                newSession.append(new CitizenAiChatService.ChatMessage(m.role(), m.content()));
            }
        }
        sessionRef[0] = newSession;
    }

    private static void refreshSummary(Label summary,
                                       String citizenName,
                                       ClientConfig.AiEndpoint[] epRef,
                                       ClientConfig.AiModel[] modelRef,
                                       CitizenChatContextResponsePacket[] ctxRef) {
        if (ctxRef[0] == null) {
            summary.setText(Component.translatable("screen.simukraft.citizen_ai.chat.loading_context"));
            return;
        }
        CitizenChatContextResponsePacket ctx = ctxRef[0];
        String modelTxt = (modelRef[0] == null || modelRef[0].name() == null || modelRef[0].name().isBlank())
                ? (modelRef[0] == null ? "-" : modelRef[0].id())
                : modelRef[0].name();
        String line = ctx.cityName() + " · Lv." + ctx.cityLevel()
                + " · " + ctx.jobKey() + " · " + ctx.personalityBrief()
                + "  |  模型: " + modelTxt;
        summary.setText(Component.literal(line));
    }

    /** rebuildMessages: 按 session.history 重建气泡列表（跳过 system）。 */
    private static void rebuildMessages(UIElement msgPanel,
                                        CitizenAiChatService.ChatSession[] sessionRef,
                                        String citizenName) {
        msgPanel.clearAllChildren();
        CitizenAiChatService.ChatSession s = sessionRef[0];
        if (s == null) {
            msgPanel.addChild(infoLine("screen.simukraft.citizen_ai.chat.ready"));
            return;
        }
        for (CitizenAiChatService.ChatMessage m : s.history) {
            if ("system".equals(m.role)) continue;
            boolean isUser = "user".equals(m.role);
            String label = isUser ? "你" : citizenName;
            msgPanel.addChild(bubble(m.content, isUser, label));
        }
    }

    /** bubble: 单条消息气泡（玩家右对齐 / 市民左对齐），支持多行 wrap。 */
    private static UIElement bubble(String text, boolean isUser, String author) {
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(2);
            layout.alignItems(AlignItems.FLEX_START);
        });
        Label authorLbl = new Label();
        authorLbl.setText(Component.literal(author));
        authorLbl.textStyle(s -> s.textColor(DIALOG_SUBTEXT).textShadow(false).fontSize(8.0F));
        authorLbl.layout(layout -> { layout.widthPercent(100); layout.height(10); });
        row.addChild(authorLbl);

        UIElement bubbleEl = new UIElement().layout(layout -> {
            layout.width(isUser ? 320 : 360);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.paddingAll(4);
            layout.gapAll(2);
        }).style(s -> s.backgroundTexture(new ColorRectTexture(isUser ? PLAYER_BUBBLE : CITIZEN_BUBBLE)));

        Label textLbl = new Label();
        textLbl.setText(Component.literal(text == null ? "" : text));
        textLbl.textStyle(s -> s.textColor(DIALOG_TEXT).textShadow(false).fontSize(9.5F).textWrap(TextWrap.WRAP));
        textLbl.layout(layout -> { layout.width(isUser ? 312 : 352); layout.height(12); });
        bubbleEl.addChild(textLbl);
        row.addChild(bubbleEl);
        return row;
    }

    /** infoLine: 状态提示行（如就绪、错误）。 */
    private static UIElement infoLine(String key) {
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(14);
            layout.paddingAll(2);
        });
        Label lbl = new Label();
        lbl.setText(Component.translatable(key));
        lbl.textStyle(s -> s.textColor(DIALOG_SUBTEXT).textShadow(false).fontSize(9.0F));
        lbl.layout(layout -> { layout.widthPercent(100); layout.height(12); });
        row.addChild(lbl);
        return row;
    }

    /** send: 发送一条用户消息并处理回复 / 错误 / loading。 */
    private static void send(CitizenAiChatService.ChatSession[] sessionRef,
                             CompletableFuture<?>[] inFlight,
                             TextField inputField,
                             Label loadingLabel,
                             Button stopBtn,
                             Button sendBtn,
                             UIElement msgPanel,
                             String citizenName,
                             String text) {
        CitizenAiChatService.ChatSession s = sessionRef[0];
        if (s == null) return;
        loadingLabel.setVisible(true);
        stopBtn.setVisible(true);
        sendBtn.setVisible(false);
        rebuildMessages(msgPanel, sessionRef, citizenName);

        CompletableFuture<String> future = CitizenAiChatService.instance().sendMessage(s, text);
        inFlight[0] = future;
        future.whenComplete((reply, err) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            mc.execute(() -> {
                inFlight[0] = null;
                loadingLabel.setVisible(false);
                stopBtn.setVisible(false);
                sendBtn.setVisible(true);
                if (err != null) {
                    CitizenAiChatService.CitizenAiChatException cae = unwrapChatException(err);
                    String msg = CitizenAiChatService.translateError(cae,
                            Minecraft.getInstance().options.languageCode);
                    msgPanel.addChild(errorLine(msg));
                    return;
                }
                // 回复成功后持久化历史：同一市民 + 同一网站下次打开仍可见上次聊天记录。
                CitizenAiChatService.saveHistory(s.citizenId, s.endpoint.baseUrl(), s.history);
                rebuildMessages(msgPanel, sessionRef, citizenName);
            });
        });
    }

    private static CitizenAiChatService.CitizenAiChatException unwrapChatException(Throwable t) {
        Throwable cur = t;
        while (cur instanceof java.util.concurrent.CompletionException && cur.getCause() != null) {
            cur = cur.getCause();
        }
        if (cur instanceof CitizenAiChatService.CitizenAiChatException cae) {
            return cae;
        }
        return null;
    }

    private static UIElement errorLine(String msg) {
        UIElement row = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(14);
            layout.paddingAll(2);
        });
        Label lbl = new Label();
        lbl.setText(Component.literal("§c" + msg));
        lbl.textStyle(s -> s.textColor(ERROR_RED).textShadow(false).fontSize(9.0F).textWrap(TextWrap.WRAP));
        lbl.layout(layout -> { layout.widthPercent(100); layout.height(12); });
        row.addChild(lbl);
        return row;
    }

    private static String ctxErrorKey(int errorCode) {
        return switch (errorCode) {
            case 1 -> "message.simukraft.citizen_ai.no_perm";
            case 2 -> "message.simukraft.citizen_ai.citizen_not_found";
            case 3 -> "message.simukraft.citizen_ai.global_disabled";
            default -> "message.simukraft.citizen_ai.unknown_error";
        };
    }

    private static Button headerButton(String key, int width) {
        Button btn = new Button();
        btn.setText(Component.translatable(key));
        btn.style(s -> s.backgroundTexture(new ColorRectTexture(0xFF795548)));
        btn.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(false).fontSize(8.5F));
        btn.layout(layout -> {
            layout.width(width);
            layout.height(16);
            layout.flexShrink(0);
            layout.justifyContent(AlignContent.CENTER);
            layout.flexDirection(FlexDirection.ROW);
        });
        return btn;
    }

    private static Button bottomButton(String key, int width) {
        Button btn = new Button();
        btn.setText(Component.translatable(key));
        btn.style(s -> s.backgroundTexture(new ColorRectTexture(0xFF5D4037)));
        btn.textStyle(s -> s.textColor(0xFFFFFFFF).textShadow(false).fontSize(9.0F));
        btn.layout(layout -> {
            layout.width(width);
            layout.height(22);
            layout.flexShrink(0);
            layout.justifyContent(AlignContent.CENTER);
            layout.flexDirection(FlexDirection.ROW);
        });
        return btn;
    }
}
