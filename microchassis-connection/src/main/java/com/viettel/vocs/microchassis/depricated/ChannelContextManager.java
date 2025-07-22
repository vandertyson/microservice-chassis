/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.depricated;

import io.netty.channel.group.ChannelGroup;

/**
 *
 * @author vttek
 */
public abstract class ChannelContextManager {
    ChannelGroup group;

    public ChannelGroup getGroup() {
        return group;
    }

    public void setGroup(ChannelGroup group) {
        this.group = group;
    }            
}
