package org.example.jmeter;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.plugin.MenuCreator;
import org.apache.jmeter.gui.tree.JMeterTreeNode;

import javax.swing.*;

public class AdaptiveCapacityMenuCreator implements MenuCreator {
    @Override
    public JMenuItem[] getMenuItemsAtLocation(MENU_LOCATION location) {
        if (location != MENU_LOCATION.TOOLS) {
            return new JMenuItem[0];
        }

        JMenuItem item = new JMenuItem("Add Adaptive Capacity Listener");
        item.addActionListener(e -> {
            GuiPackage guiPackage = GuiPackage.getInstance();
            if (guiPackage == null) {
                return;
            }

            JMeterTreeNode selectedNode = guiPackage.getTreeListener().getCurrentNode();
            JMeterTreeNode targetNode = selectedNode == null ? (JMeterTreeNode) guiPackage.getTreeModel().getRoot() : selectedNode;
            try {
                AdaptiveCapacityListenerGui gui = new AdaptiveCapacityListenerGui();
                guiPackage.getTreeModel().addComponent(gui.createTestElement(), targetNode);
            } catch (Exception ex) {
                throw new RuntimeException("Unable to add Adaptive Capacity listener to test plan", ex);
            }
        });
        return new JMenuItem[]{item};
    }

    @Override
    public JMenu[] getTopLevelMenus() {
        return new JMenu[0];
    }

    @Override
    public boolean localeChanged(MenuElement menuElement) {
        return false;
    }

    @Override
    public void localeChanged() {
        // no-op; menu text is static here
    }
}
