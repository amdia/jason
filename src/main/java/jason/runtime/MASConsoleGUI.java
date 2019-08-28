package jason.runtime;

import jason.asSemantics.Agent;
import jason.asSemantics.CircumstanceListener;
import jason.asSemantics.Event;
import jason.asSemantics.Intention;
import jason.asSyntax.Literal;
import jason.asSyntax.Term;
import jason.infra.centralised.BaseCentralisedMAS;
import jason.infra.centralised.BaseDialogGUI;
import jason.util.Pair;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.LogManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter;

/** the GUI console to output log messages */
public class MASConsoleGUI {

    protected static MASConsoleGUI masConsole        = null;
    public    static String        isTabbedPropField = MASConsoleLogHandler.class.getName() + ".tabbed";

    private boolean                isTabbed          = false;

    /** for singleton pattern */
    public static MASConsoleGUI get() {
        if (masConsole == null) {
            masConsole = new MASConsoleGUI("MAS Console");
        }
        return masConsole;
    }

    public static boolean hasConsole() {
        return masConsole != null;
    }

    protected Map<String, JTextArea>       agsTextArea       = new HashMap<String, JTextArea>();
    protected JTabbedPane                  tabPane;
    protected JFrame              frame   = null;
    protected JTextArea           output;
    protected JTextArea           outputBelief;
    protected JScrollPane         spOutput;
    protected JScrollPane		  spOutputBelief;
    protected JSplitPane		  spcenter;
    protected double			  ratio = 0.8;
    protected JButton			  toggleBelief;
    protected boolean			  displayBeliefs = false;
    protected boolean			  autoscroll = true;
    protected Agent				  beliefAgent = null;
    protected JPanel              pBt     = null;
    protected JPanel              pcenter;
    protected OutputStreamAdapter out;
    protected boolean             inPause = false;
    protected boolean			  searchWindow = false;

    protected MASConsoleGUI(String title) {
        initFrame(title);
        initMainPanel();
        initOutput();
        initButtonPanel();
    }

    protected void initFrame(String title) {
        frame = new JFrame(title);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                close();
            }
        });
        frame.getContentPane().setLayout(new BorderLayout());
        int h = 600;
        int w = (int)(h*1.618);
        frame.setBounds((int)(h*0.618), 20, w, h);
    }

    protected void initMainPanel() {
        String tabbed = LogManager.getLogManager().getProperty(isTabbedPropField);
        if (tabbed != null && tabbed.equals("true")) {
            isTabbed = true;
        }
        pcenter = new JPanel(new BorderLayout());
        if (isTabbed) {
            tabPane = new JTabbedPane(JTabbedPane.LEFT);
            pcenter.add(BorderLayout.CENTER, tabPane);
        }
        frame.getContentPane().add(BorderLayout.CENTER, pcenter);
    }

    protected void toggleAutoScroll() {
    	autoscroll = !autoscroll;
    	if(searchWindow) autoscroll = false;
    }
    
    protected void initOutput() {
        output = new JTextArea("Logs\n");
        output.setEditable(false);
        outputBelief = new JTextArea("Beliefs\n");
        outputBelief.setEditable(false);
        
        spOutput = new JScrollPane(output);
        output.addMouseListener(new MouseListener() {

			@Override
			public void mouseClicked(MouseEvent arg0) {
				toggleAutoScroll();
			}

			@Override
			public void mouseEntered(MouseEvent arg0) {}

			@Override
			public void mouseExited(MouseEvent arg0) {}

			@Override
			public void mousePressed(MouseEvent arg0) {}

			@Override
			public void mouseReleased(MouseEvent arg0) {}

        });
        
        output.addKeyListener(new KeyListener() {
			
			@Override
			public void keyTyped(KeyEvent k) {
				if(k.getKeyChar() == 'a') {
					toggleAutoScroll();
				} else if(k.getKeyChar() == 'f') {
					searchWindow();
				}
			}
			
			@Override
			public void keyReleased(KeyEvent arg0) {}
			
			@Override
			public void keyPressed(KeyEvent arg0) {}
		});
        
        ((DefaultCaret)output.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        if (isTabbed) {
            tabPane.add("common", spOutput);
        } else {
        	spOutputBelief = new JScrollPane(outputBelief);
        	spcenter = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, spOutput, spOutputBelief);

            pcenter.add(spcenter);
            spcenter.setResizeWeight(ratio);

        }
    }
    
    protected void searchWindow() {
    	autoscroll = false;
    	searchWindow = true;
    	new SearchGUI(getFrame(), "Search");
    }
    
    private class SearchGUI extends BaseDialogGUI {

        private static final long serialVersionUID = 1L;
        protected int current = 0;
        protected JLabel occurences;
        protected List<Pair<Integer,Integer>> results =new ArrayList<Pair<Integer,Integer>>();
        protected DefaultHighlightPainter colorFound = new DefaultHighlighter.DefaultHighlightPainter(Color.cyan);
        protected DefaultHighlightPainter colorSelec = new DefaultHighlighter.DefaultHighlightPainter(Color.red);
        
        public SearchGUI(Frame f, String title) {
            super(f, title);
            
            this.addWindowListener(new WindowAdapter() 
            {

              public void windowClosing(WindowEvent e)
              {
            	  output.getHighlighter().removeAllHighlights();
            	  searchWindow = false;
              }
            });
        }

        private void select(int diff) {
			current = current+diff;
			if(current==0) current = results.size();
			if(current>results.size()) current = 1;
			occurences.setText(" "+current+"/"+results.size()+" Found ");
			output.select(results.get(current-1).getFirst(), results.get(current-1).getSecond());
			color(current);
        }
        
        private void color(int index) {
        	int count = 0;
        	output.getHighlighter().removeAllHighlights();
        	for(Pair<Integer, Integer> p : results) {
        		count++;
    			try {
                	if(count == index) {
						output.getHighlighter().addHighlight(p.getFirst(), p.getSecond(), colorSelec);
            		} else {
            			output.getHighlighter().addHighlight(p.getFirst(), p.getSecond(), colorFound);
            		}
				} catch (BadLocationException e) {
					e.printStackTrace();
				}

        	}
        }
        
        protected void initComponents() {
            getContentPane().setLayout(new BorderLayout());
            JTextField jtf = new JTextField("", 30);
            
            JButton prev = new JButton("<");
            occurences = new JLabel(" Not Found ");
            JButton next = new JButton(">");
            jtf.addKeyListener(new KeyListener() {
				
				@Override
				public void keyTyped(KeyEvent e) {

				}
				
				@Override
				public void keyReleased(KeyEvent e) {
					String word = jtf.getText();
					if(word.length()>1) {
						results = new ArrayList<Pair<Integer,Integer>>();
						
				        Pattern pattern = Pattern.compile(word);
				        Matcher matcher = pattern.matcher(output.getText());
				        //matcher.matches();
				        
				        while(matcher.find()) {
				        	Pair<Integer, Integer> p = new Pair<Integer, Integer>(matcher.start(), matcher.end());
				        	try {
				        		output.getHighlighter().addHighlight(matcher.start(), matcher.end(), colorFound);
							} catch (BadLocationException e1) {
								e1.printStackTrace();
							}
				        	results.add(p);
				        }     
				        
				        if(results.size() > 0) {
				        	current = 1;
				        	select(0);
				        } else {
				        	results = new ArrayList<Pair<Integer,Integer>>();
				        	occurences.setText(" Not Found ");
				        	output.getHighlighter().removeAllHighlights();
				        }
				        
					} else {
			        	results = new ArrayList<Pair<Integer,Integer>>();
			        	occurences.setText(" Not Found ");
			        	output.getHighlighter().removeAllHighlights();
					}
				}
				
				@Override
				public void keyPressed(KeyEvent e) {}
			});
            
            prev.addMouseListener(new MouseListener() {
				
				@Override
				public void mouseReleased(MouseEvent arg0) {}
				
				@Override
				public void mousePressed(MouseEvent arg0) {}
				
				@Override
				public void mouseExited(MouseEvent arg0) {}
				
				@Override
				public void mouseEntered(MouseEvent arg0) {}
				
				@Override
				public void mouseClicked(MouseEvent arg0) {
					if(results.size()>0) {
						select(-1);
					}
				}
			});
            next.addMouseListener(new MouseListener() {
				
				@Override
				public void mouseReleased(MouseEvent arg0) {}
				
				@Override
				public void mousePressed(MouseEvent arg0) {}
				
				@Override
				public void mouseExited(MouseEvent arg0) {}
				
				@Override
				public void mouseEntered(MouseEvent arg0) {}
				
				@Override
				public void mouseClicked(MouseEvent arg0) {
					if(results.size()>0) {
						select(1);
					}
				}
			});            
            getContentPane().add(jtf, BorderLayout.NORTH);
            getContentPane().add(prev, BorderLayout.WEST);
            getContentPane().add(occurences, BorderLayout.CENTER);
            getContentPane().add(next, BorderLayout.EAST);
        }

        protected boolean ok() {
            return true;
        }
    }
    
    protected void toggleBeliefs() {
    	if(spcenter.getRightComponent()!=null)
    		spcenter.remove(spOutputBelief);
    	else 
    		spcenter.setRightComponent(spOutputBelief);
    	spcenter.setDividerLocation(ratio);
    	
    	displayBeliefs = !displayBeliefs;
    }

    class LiteralTimeComparator implements Comparator<Literal> {
        @Override
        public int compare(Literal a, Literal b) {
            return (int) (getAddTime(a) - getAddTime(b)) ;
        }
    }
    
    public double getAddTime(Literal l) {
    	double result = 0;
    	Literal addTime = l.getAnnot("add_time");
    	if(addTime != null) {
    		Term time = addTime.getTerm(0);
    		if(time != null) {
    			result = Double.parseDouble(time.toString());
    		}
    	}
    	return result;
    }
    
    public void updateBeliefList() {
    	if(beliefAgent != null) {
    		toggleBelief.setEnabled(true);
    		if(spcenter.getRightComponent() == null && displayBeliefs) {
    			spcenter.setRightComponent(spOutputBelief);
    			spcenter.setDividerLocation(ratio);
    		}
    		
    		outputBelief.setText("Beliefs\n");
    		
    		Iterator<Literal> it = beliefAgent.getBB().iterator();
    		List<Literal> list = new ArrayList<Literal>();
			while (it.hasNext()) {
				list.add(it.next());
			}
			Comparator<Literal> cmp = new LiteralTimeComparator();
			Collections.sort(list, cmp);
    	    it = list.iterator();
    		while(it.hasNext()) {
    			Literal l = it.next();
    			String terms = "";
    			if(l.getTerms() != null)
    				terms = l.getTerms().toString();
    			
    			outputBelief.append("  "+l.getFunctor().toString()+terms+"\n");
    		}
    	} else {
    		spcenter.remove(spOutputBelief);
    		toggleBelief.setEnabled(false);
    	}

    }
    
    public void setBeliefAgent(Agent ag) {
    	displayBeliefs = true;
    	beliefAgent = ag;

    	CircumstanceListener cl = new CircumstanceListener() {

            public void eventAdded(Event e) {
            	updateBeliefList();
            }

            public void intentionAdded(Intention i) {}

			@Override
			public void intentionDropped(Intention i) {}

			@Override
			public void intentionSuspended(Intention i, String reason) {}

			@Override
			public void intentionResumed(Intention i) {}
        };
        beliefAgent.getTS().getC().addEventListener(cl);
    	updateBeliefList();
    }

    public void cleanConsole() {
        output.setText("");
    }

    protected void initButtonPanel() {
        pBt = new JPanel();
        pBt.setLayout(new FlowLayout(FlowLayout.CENTER));

        frame.getContentPane().add(BorderLayout.SOUTH, pBt);

        JButton btClean = new JButton("Clean", new ImageIcon(BaseCentralisedMAS.class.getResource("/images/clear.gif")));
        btClean.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                cleanConsole();
            }
        });

        addButton(btClean);
        
        toggleBelief = new JButton("Beliefs");
        toggleBelief.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
            	toggleBeliefs();
            }
        });
        addButton(toggleBelief);
        updateBeliefList();
    }

    public void setTitle(String s) {
        frame.setTitle(s);
    }

    public JFrame getFrame() {
        return frame;
    }

    public void addButton(JButton jb) {
        pBt.add(jb);
        pBt.revalidate();
        // pack();
    }

    synchronized public void setPause(boolean b) {
        inPause = b;
        notifyAll();
    }

    synchronized void waitNotPause() {
        try {
            while (inPause) {
                wait();
            }
        } catch (Exception e) { }
    }

    public boolean isTabbed() {
        return isTabbed;
    }
    public boolean isPause() {
        return inPause;
    }

    public void append(String s) {
        append(null, s);
    }

    public void append(final String agName, String s) {
        try {
            if (!frame.isVisible()) {
                frame.setVisible(true);
            }
            if (inPause) {
                waitNotPause();
            }
            if (isTabbed && agName != null) {
                JTextArea ta = agsTextArea.get(agName);
                if (ta == null) {
                    ta = new JTextArea();
                    ta.setEditable(false);
                    ((DefaultCaret)ta.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
                    final JTextArea cta = ta;
                    SwingUtilities.invokeAndWait(new Runnable() {
                        public void run() {
                            agsTextArea.put(agName, cta);
                            tabPane.add(agName, new JScrollPane(cta));
                        }
                    });
                }
                if (ta != null) {
                    if (ta.getDocument().getLength() > 100000) {
                        ta.setText("");
                    }
                    ta.append(s);
                }
            }

            // print in output
            synchronized (output) {
                try {
//                    if (output.getDocument().getLength() > 60000) {
//                        cleanConsole();
//                    }
                    output.append(s);
                } catch (IllegalArgumentException e) {
                }
            }
        } catch (Exception e) {
            try {
                PrintWriter out = new PrintWriter(new FileWriter("e_r_r_o_r.txt"));
                out.write("Error that can not be printed in the MAS Console!\n"+e.toString()+"\n");
                e.printStackTrace(out);
                out.close();
            } catch (IOException e1) {
            }
        }
        
        if(autoscroll) {
        	JScrollBar sb = spOutput.getVerticalScrollBar();
        	sb.setValue( sb.getMaximum() );
        }
    }

    public void close() {
        setPause(false);
        if (masConsole != null && masConsole.frame != null)
            masConsole.frame.setVisible(false);
        if (out != null)
            out.restoreOriginalOut();
        try {
            if (BaseCentralisedMAS.getRunner() != null) {
                FileWriter f = new FileWriter(BaseCentralisedMAS.stopMASFileName);
                f.write(32);
                f.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        masConsole = null;
    }

    public void setAsDefaultOut() {
        out = new OutputStreamAdapter(this, null);
        out.setAsDefaultOut();
    }

}
