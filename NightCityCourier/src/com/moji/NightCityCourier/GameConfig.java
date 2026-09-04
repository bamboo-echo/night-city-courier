package com.moji.NightCityCourier;
import java.util.*;

/**
 * 游戏常量配置类，集中管理所有可调参数。
 * 使用不可实例化的工具类模式，所有字段均为静态常量。
 */
public final class GameConfig {

    /** 全局共享的随机数生成器，确保可复现性 */
    public static final Random RANDOM = new Random();

    // ────────── 遭遇事件相关 ──────────

    /** 任务风险系数对遭遇概率的加成倍率 */
    public static final double ENCOUNTER_RISK_FACTOR = 0.2;
    /** 单步遭遇概率上限 */
    public static final double ENCOUNTER_CHANCE_CAP = 0.95;
    /** 每级闪避技能可自动避开事件的概率 */
    public static final double AVOID_EVENT_CHANCE_PER_LEVEL = 0.15;

    // ────────── 治疗系统 ──────────

    public static final int HEAL_BASE_COST = 100;
    public static final int HEAL_COST_PER_LEVEL = 15;
    /** 成功完成一次任务后的生命恢复量 */
    public static final int TASK_SUCCESS_HEAL = 15;
    /** 每次治疗恢复的生命值 */
    public static final int HEAL_AMOUNT = 30;

    // ────────── 警察贿赂 ──────────

    public static final int POLICE_BRIBE_BASE = 80;
    public static final int POLICE_BRIBE_PER_LEVEL = 20;
    public static final double BASE_ESCAPE_CHANCE = 0.4;
    public static final double ESCAPE_PER_SPEED = 0.1;
    public static final double ESCAPE_PER_AVOID_POLICE = 0.1;

    // ────────── 帮派贿赂 ──────────

    public static final int GANG_BRIBE_BASE = 100;
    public static final int GANG_BRIBE_PER_LEVEL = 30;
    public static final double BASE_FIGHT_CHANCE = 0.4;
    public static final double FIGHT_PER_AVOID_GANG = 0.1;

    // ────────── 升级系统 ──────────

    public static final int UPGRADE_BASE_COST = 100;
    public static final int UPGRADE_COST_PER_LEVEL = 50;
    public static final int UPGRADE_COST_PER_UPGRADE = 20;

    // ────────── 事件权重（加权随机抽取） ──────────

    public static final int POLICE_WEIGHT = 20;
    public static final int GANG_WEIGHT = 20;
    public static final int BREAKDOWN_WEIGHT = 10;
    public static final int RIVAL_WEIGHT = 10;
    public static final int RESCUE_WEIGHT = 8;
    public static final int GHOST_WEIGHT = 5;
    public static final int GAMBLE_WEIGHT = 5;
    public static final int SCAN_WEIGHT = 6;
    public static final int SCAVENGER_WEIGHT = 6;

    /** 通缉等级上限 */
    public static final int WANTED_MAX = 5;

    // ────────── 任务变体概率 ──────────

    /** 任务出现特殊变体的基础概率 */
    public static final double VARIANT_CHANCE = 0.4;
    /** 竞速件公平竞速的基础胜率 */
    public static final double RACE_WIN_BASE = 0.5;
    /** 竞速件每点速度增加的胜率 */
    public static final double RACE_WIN_PER_SPEED = 0.1;
    /** 竞速件获胜的奖励比例（基于任务报酬） */
    public static final double RACE_BONUS_RATIO = 0.25;
    /** 竞速件失败的罚金比例（基于任务报酬） */
    public static final double RACE_PENALTY_RATIO = 0.125;
    /** 救命件加价比例（基于任务报酬） */
    public static final double LIFESAVER_GOUGE_RATIO = 0.3;
    /** 错送件返回的额外奖励比例（基于任务报酬） */
    public static final double REROUTE_BONUS_RATIO = 0.3;

    /** 陷阱件逃跑增加的通缉等级 */
    public static final int TRAP_WANTED_INCREASE = 1;
    /** 任务失败的最低罚金 */
    public static final int FAIL_PENALTY_MIN = 0;

    /** 九道门中强制面对的门数阈值 */
    public static final int GATES_FORCE_FACE_MIN = 3;

    // ────────── 回响石来源标识 ──────────

    public static final String ECHO_RESCUE_LOOT = "RESCUE_LOOT";
    public static final String ECHO_EVIDENCE_BETRAY = "EVIDENCE_BETRAY";
    public static final String ECHO_LIVING_DIED = "LIVING_DIED";
    public static final String ECHO_LEGACY_KEPT = "LEGACY_KEPT";
    public static final String ECHO_RESCUE_SAVED = "RESCUE_SAVED";
    public static final String ECHO_CELEB_ESCORT = "CELEB_ESCORT";
    public static final String ECHO_CELEB_ESCORT_BOND = "CELEB_ESCORT_BOND";
    public static final String ECHO_CONFESSION_HONEST = "CONFESSION_HONEST";
    public static final String ECHO_LIFESAVER_SPARED = "LIFESAVER_SPARED";
    public static final String ECHO_LEGACY_RETURNED = "LEGACY_RETURNED";
    public static final String ECHO_LIVING_SAVED = "LIVING_SAVED";
    public static final String ECHO_BRIBE_POLICE = "BRIBE_POLICE";
    public static final String ECHO_BRIBE_GANG = "BRIBE_GANG";
    public static final String ECHO_RIVAL_GIVEUP = "RIVAL_GIVEUP";
    public static final String ECHO_SCAN_DETOUR = "SCAN_DETOUR";
    public static final String ECHO_GHOST_ACCEPT = "GHOST_ACCEPT";
    public static final String ECHO_TRAP_SURRENDER = "TRAP_SURRENDER";
    public static final String ECHO_RIVAL_COOPERATE = "RIVAL_COOPERATE";
    public static final String ECHO_GHOST_TRACKED = "GHOST_TRACKED";
    public static final String ECHO_GAMBLE_REPORT = "GAMBLE_REPORT";
    public static final String ECHO_SILENCE = "SILENCE";
    public static final String ECHO_SCAVENGER_DISMISS = "SCAVENGER_DISMISS";
    public static final String ECHO_SCAVENGER_INTEL = "SCAVENGER_INTEL";
    public static final String ECHO_BLACK_MARKET = "BLACK_MARKET";
    public static final String ECHO_LIFESAVER_GOUGED = "LIFESAVER_GOUGED";
    public static final String ECHO_RACE_HOG = "RACE_HOG";

    /** 逃离夜之城所需的目标金额 */
    public static final int WIN_TARGET = 8000;

    private GameConfig() {}
}