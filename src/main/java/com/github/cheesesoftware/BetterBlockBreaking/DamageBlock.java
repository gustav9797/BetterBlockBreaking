package com.github.cheesesoftware.BetterBlockBreaking;

import java.util.Date;

import org.bukkit.Location;

public class DamageBlock {

    private Location l;
    private Date dateDamaged = null;
    private Date lastFade = null;

    public DamageBlock(Location l) {
	this.l = l;
    }

    public Location getLocation() {
	return this.l;
    }

    public Date getDamageDate() {
	return this.dateDamaged;
    }

    public void setDamageDate() {
	this.dateDamaged = new Date();
    }

    public Date getLastFadeDate() {
	return this.lastFade;
    }

    public void setLastFadeDate() {
	this.lastFade = new Date();
    }
}
