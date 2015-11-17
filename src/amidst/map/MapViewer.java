package amidst.map;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JComponent;
import javax.swing.JPopupMenu;

import amidst.Options;
import amidst.gui.menu.PlayerMenuItemFactory;
import amidst.logging.Log;
import amidst.map.layer.PlayerLayer;
import amidst.map.object.MapObject;
import amidst.map.object.MapObjectPlayer;
import amidst.map.widget.BiomeToggleWidget;
import amidst.map.widget.BiomeWidget;
import amidst.map.widget.CursorInformationWidget;
import amidst.map.widget.DebugWidget;
import amidst.map.widget.FpsWidget;
import amidst.map.widget.PanelWidget;
import amidst.map.widget.PanelWidget.CornerAnchorPoint;
import amidst.map.widget.ScaleWidget;
import amidst.map.widget.SeedWidget;
import amidst.map.widget.SelectedObjectWidget;
import amidst.map.widget.Widget;
import amidst.minecraft.MinecraftUtil;
import amidst.minecraft.world.World;
import amidst.resources.ResourceLoader;

public class MapViewer {
	private class Listeners implements MouseListener, MouseWheelListener,
			KeyListener {
		private Widget mouseOwner;

		@Override
		public void keyTyped(KeyEvent e) {
		}

		@Override
		public void keyPressed(KeyEvent e) {
			Point mouse = getMousePositionOrCenterFromComponent();
			if (e.getKeyCode() == KeyEvent.VK_EQUALS) {
				zoom.adjustZoom(mouse, -1);
			} else if (e.getKeyCode() == KeyEvent.VK_MINUS) {
				zoom.adjustZoom(mouse, 1);
			}
		}

		@Override
		public void keyReleased(KeyEvent e) {
		}

		@Override
		public void mouseWheelMoved(MouseWheelEvent e) {
			int notches = e.getWheelRotation();
			Point mouse = getMousePositionFromEvent(e);
			Widget widget = findWidget(mouse);
			if (widget != null
					&& widget.onMouseWheelMoved(
							translateMouseXCoordinateToWidget(mouse, widget),
							translateMouseYCoordinateToWidget(mouse, widget),
							notches)) {
				// noop
			} else {
				zoom.adjustZoom(mouse, notches);
			}
		}

		@Override
		public void mouseClicked(MouseEvent e) {
			if (e.isMetaDown()) {
				return;
			}
			Point mouse = getMousePositionFromEvent(e);
			Widget widget = findWidget(mouse);
			if (widget != null
					&& widget.onClick(
							translateMouseXCoordinateToWidget(mouse, widget),
							translateMouseYCoordinateToWidget(mouse, widget))) {
				// noop
			} else {
				mouseClickedOnMap(mouse);
			}
		}

		@Override
		public void mousePressed(MouseEvent e) {
			Point mouse = getMousePositionFromEvent(e);
			if (e.isPopupTrigger()) {
				showMenu(e);
			} else if (e.isMetaDown()) {
			} else if (mousePressedOnWidget(e, mouse)) {
			} else {
				lastMouse = mouse;
			}
		}

		private boolean mousePressedOnWidget(MouseEvent e, Point mouse) {
			Widget widget = findWidget(mouse);
			if (widget != null
					&& widget.onMousePressed(
							translateMouseXCoordinateToWidget(mouse, widget),
							translateMouseYCoordinateToWidget(mouse, widget))) {
				mouseOwner = widget;
				return true;
			} else {
				return false;
			}
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			if (e.isPopupTrigger()) {
				showMenu(e);
			} else if (mouseOwner != null) {
				mouseOwner.onMouseReleased();
				mouseOwner = null;
			} else {
				lastMouse = null;
			}
		}

		@Override
		public void mouseEntered(MouseEvent e) {
		}

		@Override
		public void mouseExited(MouseEvent e) {
		}

		private void showMenu(MouseEvent e) {
			if (MinecraftUtil.getVersion().saveEnabled()) {
				lastRightClick = getMousePositionFromEvent(e);
				if (world.isFileWorld()) {
					menu.show(e.getComponent(), e.getX(), e.getY());
				}
			}
		}

		private void mouseClickedOnMap(Point mouse) {
			selectedMapObject = map.getObjectAt(mouse, 50.0);
			map.setSelectedMapObject(selectedMapObject);
		}

		/**
		 * Don't use getMousePosition() of the JComponent in mouse events,
		 * because when computer is swapping/grinding, mouse may have moved out
		 * of window before execution reaches here.
		 */
		private Point getMousePositionFromEvent(MouseEvent e) {
			return e.getPoint();
		}

		private Widget findWidget(Point mouse) {
			for (Widget widget : widgets) {
				if (widget.isVisible() && isMouseInWidgetBounds(mouse, widget)) {
					return widget;
				}
			}
			return null;
		}

		private int translateMouseXCoordinateToWidget(Point mouse, Widget widget) {
			return mouse.x - widget.getX();
		}

		private int translateMouseYCoordinateToWidget(Point mouse, Widget widget) {
			return mouse.y - widget.getY();
		}

		private boolean isMouseInWidgetBounds(Point mouse, Widget widget) {
			return mouse.x > widget.getX() && mouse.y > widget.getY()
					&& mouse.x < widget.getX() + widget.getWidth()
					&& mouse.y < widget.getY() + widget.getHeight();
		}
	}

	@SuppressWarnings("serial")
	private class Component extends JComponent {
		private long lastTime = System.currentTimeMillis();

		@Override
		public void paint(Graphics g) {
			Graphics2D g2d = (Graphics2D) g.create();
			float time = calculateTimeSpanSinceLastDrawInSeconds();

			clear(g2d);

			updateMapZoom();
			updateMapMovement();

			setViewerDimensions();

			drawMap(g2d, time);
			drawBorder(g2d);
			drawWidgets(g2d, time);
		}

		private float calculateTimeSpanSinceLastDrawInSeconds() {
			long currentTime = System.currentTimeMillis();
			float result = Math.min(Math.max(0, currentTime - lastTime), 100) / 1000.0f;
			lastTime = currentTime;
			return result;
		}

		private void clear(Graphics2D g2d) {
			g2d.setColor(Color.black);
			g2d.fillRect(0, 0, this.getWidth(), this.getHeight());
		}

		private void updateMapZoom() {
			zoom.update(map);
		}

		private void updateMapMovement() {
			movement.update();
		}

		private void setViewerDimensions() {
			map.setViewerWidth(getWidth());
			map.setViewerHeight(getHeight());
		}

		public void drawMap(Graphics2D g2d, float time) {
			g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
					RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			map.draw((Graphics2D) g2d.create(), time);
		}

		private void drawBorder(Graphics2D g2d) {
			int width10 = getWidth() - 10;
			int height10 = getHeight() - 10;
			int width20 = getWidth() - 20;
			int height20 = getHeight() - 20;
			g2d.drawImage(DROP_SHADOW_TOP_LEFT, 0, 0, null);
			g2d.drawImage(DROP_SHADOW_TOP_RIGHT, width10, 0, null);
			g2d.drawImage(DROP_SHADOW_BOTTOM_LEFT, 0, height10, null);
			g2d.drawImage(DROP_SHADOW_BOTTOM_RIGHT, width10, height10, null);
			g2d.drawImage(DROP_SHADOW_TOP, 10, 0, width20, 10, null);
			g2d.drawImage(DROP_SHADOW_BOTTOM, 10, height10, width20, 10, null);
			g2d.drawImage(DROP_SHADOW_LEFT, 0, 10, 10, height20, null);
			g2d.drawImage(DROP_SHADOW_RIGHT, width10, 10, 10, height20, null);
		}

		public void drawWidgets(Graphics2D g2d, float time) {
			g2d.setFont(textFont);
			for (Widget widget : widgets) {
				if (widget.isVisible()) {
					g2d.setComposite(AlphaComposite.getInstance(
							AlphaComposite.SRC_OVER, widget.getAlpha()));
					widget.draw(g2d, time);
				}
			}
		}
	}

	private class MapMovement {
		private Point2D.Double speed = new Point2D.Double();

		public void update() {
			updateMapMovementSpeed();
			moveMap();
			throttleMapMovementSpeed();
		}

		private void updateMapMovementSpeed() {
			if (lastMouse != null) {
				Point currentMouse = component.getMousePosition();
				if (currentMouse != null) {
					double dX = currentMouse.x - lastMouse.x;
					double dY = currentMouse.y - lastMouse.y;
					// TODO : Scale with time
					speed.setLocation(dX * 0.2, dY * 0.2);
				}
				lastMouse.translate((int) speed.x, (int) speed.y);
			}
		}

		private void moveMap() {
			map.moveBy((int) speed.x, (int) speed.y);
		}

		private void throttleMapMovementSpeed() {
			if (Options.instance.mapFlicking.get()) {
				speed.x *= 0.95f;
				speed.y *= 0.95f;
			} else {
				speed.x = 0;
				speed.y = 0;
			}
		}
	}

	private static class MapZoom {
		// TODO: make these non-static! They are static to keep the zoom level
		// after loading a new map.
		private static int remainingTicks = 0;
		private static int level = 0;
		private static double target = 0.25f;
		private static double current = 0.25f;

		private Point zoomMouse = new Point();

		public void update(Map map) {
			remainingTicks--;
			if (remainingTicks >= 0) {
				double previous = current;
				current = (target + current) * 0.5;

				Point2D.Double targetZoom = map.getScaled(previous, current,
						zoomMouse);
				map.moveBy(targetZoom);
				map.setZoom(current);
			}
		}

		public void adjustZoom(Point position, int notches) {
			zoomMouse = position;
			if (notches > 0) {
				if (level < getMaxZoomLevel()) {
					target /= 1.1;
					level++;
					remainingTicks = 100;
				}
			} else if (level > -20) {
				target *= 1.1;
				level--;
				remainingTicks = 100;
			}
		}

		private int getMaxZoomLevel() {
			if (Options.instance.maxZoom.get()) {
				return 10;
			} else {
				return 10000;
			}
		}

		public double getCurrentValue() {
			return current;
		}
	}

	private static final BufferedImage DROP_SHADOW_BOTTOM_LEFT = ResourceLoader
			.getImage("dropshadow/inner_bottom_left.png");
	private static final BufferedImage DROP_SHADOW_BOTTOM_RIGHT = ResourceLoader
			.getImage("dropshadow/inner_bottom_right.png");
	private static final BufferedImage DROP_SHADOW_TOP_LEFT = ResourceLoader
			.getImage("dropshadow/inner_top_left.png");
	private static final BufferedImage DROP_SHADOW_TOP_RIGHT = ResourceLoader
			.getImage("dropshadow/inner_top_right.png");
	private static final BufferedImage DROP_SHADOW_BOTTOM = ResourceLoader
			.getImage("dropshadow/inner_bottom.png");
	private static final BufferedImage DROP_SHADOW_TOP = ResourceLoader
			.getImage("dropshadow/inner_top.png");
	private static final BufferedImage DROP_SHADOW_LEFT = ResourceLoader
			.getImage("dropshadow/inner_left.png");
	private static final BufferedImage DROP_SHADOW_RIGHT = ResourceLoader
			.getImage("dropshadow/inner_right.png");

	private Listeners listeners = new Listeners();
	private Component component = new Component();
	private MapZoom zoom = new MapZoom();
	private MapMovement movement = new MapMovement();

	private Timer updateTimer = new Timer();

	private World world;
	private LayerContainer layerContainer;
	private FragmentManager fragmentManager;

	private JPopupMenu menu = new JPopupMenu();
	public int strongholdCount;
	public int villageCount;

	private Map map;
	private MapObject selectedMapObject = null;
	private Point lastMouse;
	public Point lastRightClick = null;

	private Font textFont = new Font("arial", Font.BOLD, 15);
	private FontMetrics textMetrics;

	private List<Widget> widgets = new ArrayList<Widget>();

	public MapViewer(World world, LayerContainer layerContainer,
			FragmentManager fragmentManager) {
		this.world = world;
		this.layerContainer = layerContainer;
		this.fragmentManager = fragmentManager;
		initPlayerMenu();
		initMap();
		initWidgets();
		initComponent();
		initTimer();
	}

	private void initPlayerMenu() {
		if (world.isFileWorld()) {
			PlayerLayer playerLayer = layerContainer.getPlayerLayer();
			PlayerMenuItemFactory factory = new PlayerMenuItemFactory(this,
					playerLayer);
			for (MapObjectPlayer player : playerLayer.getPlayers()) {
				menu.add(factory.create(player));
			}
		}
	}

	private void initMap() {
		map = new Map(fragmentManager);
		map.setZoom(zoom.getCurrentValue());
		map.setSelectedMapObject(selectedMapObject);
	}

	private void initWidgets() {
		initWidget(new FpsWidget(this), CornerAnchorPoint.BOTTOM_LEFT);
		initWidget(new ScaleWidget(this), CornerAnchorPoint.BOTTOM_CENTER);
		initWidget(new SeedWidget(this), CornerAnchorPoint.TOP_LEFT);
		initWidget(new DebugWidget(this), CornerAnchorPoint.BOTTOM_RIGHT);
		initWidget(new SelectedObjectWidget(this), CornerAnchorPoint.TOP_LEFT);
		initWidget(new CursorInformationWidget(this),
				CornerAnchorPoint.TOP_RIGHT);
		initWidget(new BiomeToggleWidget(this), CornerAnchorPoint.BOTTOM_RIGHT);
		initWidget(new BiomeWidget(this), CornerAnchorPoint.NONE);
	}

	private void initWidget(PanelWidget widget, CornerAnchorPoint anchorPoint) {
		widgets.add(widget.setAnchorPoint(anchorPoint));
	}

	private void initComponent() {
		component.addMouseListener(listeners);
		component.addMouseWheelListener(listeners);
		component.setFocusable(true);
		textMetrics = component.getFontMetrics(textFont);
	}

	private void initTimer() {
		updateTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				component.repaint();
			}
		}, 20, 20);
	}

	public BufferedImage createCaptureImage() {
		BufferedImage image = new BufferedImage(map.getViewerWidth(),
				map.getViewerHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		component.drawMap(g2d, 0);
		component.drawWidgets(g2d, 0);
		g2d.dispose();
		return image;
	}

	private Point getMousePositionOrCenterFromComponent() {
		Point mouse = component.getMousePosition();
		if (mouse == null) {
			mouse = new Point(component.getWidth() >> 1,
					component.getHeight() >> 1);
		}
		return mouse;
	}

	public void dispose() {
		Log.debug("Disposing of map viewer.");
		updateTimer.cancel();
		map.dispose();
		menu.removeAll();
		updateTimer = null;
	}

	public void centerAt(long x, long y) {
		map.centerOn(x, y);
	}

	public MapObject getSelectedObject() {
		return selectedMapObject;
	}

	public FragmentManager getFragmentManager() {
		return fragmentManager;
	}

	public Map getMap() {
		return map;
	}

	@Deprecated
	public FontMetrics getFontMetrics() {
		return textMetrics;
	}

	@Deprecated
	public FontMetrics getFontMetrics(Font font) {
		return component.getFontMetrics(font);
	}

	public KeyListener getKeyListener() {
		return listeners;
	}

	public JComponent getComponent() {
		return component;
	}

	@Deprecated
	public Point getMousePosition() {
		return component.getMousePosition();
	}

	@Deprecated
	public int getWidth() {
		return component.getWidth();
	}

	@Deprecated
	public int getHeight() {
		return component.getHeight();
	}
}
