package eu.spitfire_project;
/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

import java.awt.*;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

import javax.swing.*;

public class VisualizerServer {

    private final int port;

    private static JFrame appFrame = new JFrame();
    private static THouseView houseView;

    public VisualizerServer(int port) {
        this.port = port;
    }

    public void run() {
        // Configure the server.
        ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(
                Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));

        // Set up the event pipeline factory.
        bootstrap.setPipelineFactory(new ServerPipelineFactory(houseView));

        // Bind and start to accept incoming connections.
        bootstrap.bind(new InetSocketAddress(port));
    }

    public static void main(String args[]) {
        appFrame.setMinimumSize(new Dimension(800, 480));
        appFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        //Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        houseView = new THouseView(appFrame);
        appFrame.add(houseView);
        appFrame.pack();
        appFrame.setVisible(true);

        //Waiting for commands...
        new VisualizerServer(10000).run();
    }
}
