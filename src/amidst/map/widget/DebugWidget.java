package amidst.map.widget;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

import amidst.Options;
import amidst.map.FragmentManager;
import amidst.map.MapViewer;

public class DebugWidget extends PanelWidget {
	public DebugWidget(MapViewer mapViewer) {
		super(mapViewer);
		forceVisibility(onVisibilityCheck());
	}

	@Override
	public void draw(Graphics2D g2d, float time) {
		List<String> panelLines = getPanelLines(map.getFragmentManager());
		int width = getPanelWidth(panelLines, mapViewer.getFontMetrics());
		int height = getPanelHeight(panelLines);
		setSize(width, height);
		super.draw(g2d, time);
		drawPanelLines(g2d, panelLines);
	}

	private List<String> getPanelLines(FragmentManager fragmentManager) {
		List<String> panelLines = new ArrayList<String>();
		panelLines.add("Fragment Manager:");
		panelLines.add("Pool Size: " + fragmentManager.getCacheSize());
		panelLines.add("Free Queue Size: "
				+ fragmentManager.getFreeFragmentQueueSize());
		panelLines.add("Request Queue Size: "
				+ fragmentManager.getRequestQueueSize());
		panelLines.add("Recycle Queue Size: "
				+ fragmentManager.getRecycleQueueSize());
		panelLines.add("");
		panelLines.add("Map Viewer:");
		panelLines.add("Map Size: " + map.getFragmentsPerRow() + "x"
				+ map.getFragmentsPerColumn() + " ["
				+ (map.getFragmentsPerRow() * map.getFragmentsPerColumn())
				+ "]");
		return panelLines;
	}

	private int getPanelWidth(List<String> panelLines, FontMetrics fontMetrics) {
		int result = 0;
		for (String line : panelLines) {
			int textWidth = fontMetrics.stringWidth(line);
			if (result < textWidth) {
				result = textWidth;
			}
		}
		return result + 20;
	}

	private int getPanelHeight(List<String> panelLines) {
		return panelLines.size() * 20 + 10;
	}

	private void drawPanelLines(Graphics2D g2d, List<String> panelLines) {
		g2d.setColor(TEXT_COLOR);
		for (int i = 0; i < panelLines.size(); i++) {
			g2d.drawString(panelLines.get(i), getX() + 10, getY() + 20 + i * 20);
		}
	}

	@Override
	protected boolean onVisibilityCheck() {
		return Options.instance.showDebug.get();
	}
}
