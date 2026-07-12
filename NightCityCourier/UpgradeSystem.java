package com.moji.NightCityCourier;

/**
 * 升级系统，处理玩家属性升级的费用计算和执行。
 * 升级费用随玩家等级和已升级次数递增。
 */
public class UpgradeSystem {

    /**
     * 执行一次属性升级。
     *
     * @param player 玩家对象
     * @param type   升级类型：1=速度，2=反追踪（避警），3=帮派威慑（避帮）
     */
    public void upgrade(Player player, int type) {
        int totalUpgrades = (player.getSpeed() - 1) + player.getAvoidPolice() + player.getAvoidGang();

        int cost = GameConfig.UPGRADE_BASE_COST
                + player.getLevel() * GameConfig.UPGRADE_COST_PER_LEVEL
                + totalUpgrades * GameConfig.UPGRADE_COST_PER_UPGRADE;
        if (player.getMoney() < cost) {
            System.out.println("钱不够！需要 " + cost + "€");
            return;
        }
        switch (type) {
            case 1 -> player.addSpeed();
            case 2 -> player.addAvoidPolice();
            case 3 -> player.addAvoidGang();
            default -> {
                System.out.println("取消升级");
                return;
            }
        }
        player.costMoney(cost);
        System.out.println("升级成功！");
    }
}