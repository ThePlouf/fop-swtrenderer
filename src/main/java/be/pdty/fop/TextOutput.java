/*
 * Copyright 2019 Philippe Detournay
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package be.pdty.fop;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.PathData;
import org.eclipse.swt.graphics.RGBA;

public class TextOutput {
  private static class Request {
    public float x;
    public float y;
    public String text;
    public String font;
    public int fontSize;
    public float length;
    
    public Request(float px,float py,String t,String f,int s,float l) {
      x=px;
      y=py;
      text=t;
      font=f;
      fontSize=s;
      length=l;
    }
  }
  
  private static class Metrics {
    public float left;
    public float right;
    public float top;
    public float bottom;
    public float strength;
  }
  
  private GCWrapper gc;
  private boolean def;
  
  private RGBA underline;
  private List<Request> underlineRequests;
  private RGBA strike;
  private List<Request> strikeRequests;
  private RGBA overline;
  private List<Request> overlineRequests;
    
  public TextOutput(GCWrapper wrapper,boolean deferred) {
    gc=wrapper;
    def=deferred;
    underlineRequests=new ArrayList<>();
    strikeRequests=new ArrayList<>();
    overlineRequests=new ArrayList<>();
  }
  
  private Metrics metrics(List<Request> requests) {
    Metrics ans=new Metrics();

    ans.left=Float.MAX_VALUE;
    ans.right=Float.MIN_VALUE;
    ans.top=Float.MAX_VALUE;
    ans.bottom=Float.MIN_VALUE;
    ans.strength=Float.MIN_VALUE;
    
    for(Request r:requests) {
      float left=r.x;
      float right=r.x+r.length;
      float top=r.y;
      float bottom=r.y+r.fontSize/1000.0f;
      float strength=r.fontSize/1000f/12.0f;
      
      if(left<ans.left) ans.left=left;
      if(right>ans.right) ans.right=right;
      if(top<ans.top) ans.top=top;
      if(bottom>ans.bottom) ans.bottom=bottom;
      if(strength>ans.strength) ans.strength=strength;
    }
    
    return ans;
    
  }
  
  private void line(float left,float right,float y,float s,RGBA color) {
    PathData path=new PathData();
    path.types=new byte[] {
        SWT.PATH_MOVE_TO,
        SWT.PATH_LINE_TO,
        SWT.PATH_LINE_TO,
        SWT.PATH_LINE_TO,
        SWT.PATH_CLOSE};
    path.points=new float[] {
        left,y-s/2,
        right,y-s/2,
        right,y+s/2,
        left,y+s/2};
    
    gc.setColor(color);
    if(def) {
      gc.fillPathDeferred(path);
    } else {
      gc.fillPath(path);
    }
  }
  
  private void closeUnderline() {
    if(underline==null || underlineRequests.size()==0) return;
    
    Metrics m=metrics(underlineRequests);
    
    //TODO perform the skip-ink
    line(m.left,m.right,m.bottom,m.strength,underline);
    
    underlineRequests.clear();
  }
  
  private void closeStrike() {
    if(strike==null || strikeRequests.size()==0) return;
    
    Metrics m=metrics(strikeRequests);
    line(m.left,m.right,(m.bottom*2+m.top)/3,m.strength,strike);
    
    strikeRequests.clear();
  }
  
  private void closeOverline() {
    if(overline==null || overlineRequests.size()==0) return;
    
    Metrics m=metrics(overlineRequests);
    line(m.left,m.right,m.top,m.strength,overline);
    
    overlineRequests.clear();
  }
  
  private boolean different(RGBA a,RGBA b) {
    if(a==b) return false;
    if(a==null || b==null) return true;
    return !a.equals(b);
  }
  
  public void text(String s,float x,float y,String font,int fontSize,RGBA color,RGBA underlineColor,RGBA strikeColor,RGBA overlineColor) {
    if(different(underlineColor,underline)) closeUnderline();
    if(different(strikeColor,strike)) closeStrike();
    if(different(overlineColor,overline)) closeOverline();
    
    underline=underlineColor;
    strike=strikeColor;
    overline=overlineColor;
    
    gc.setFont(font,fontSize);
    gc.setColor(color);
    gc.drawString(s,x,y);
    
    if(underline!=null || strike!=null || overline!=null) {
      Request r=new Request(x,y,s,font,fontSize,gc.stringExtentWidth(s));
      
      if(underline!=null) underlineRequests.add(r);
      if(strike!=null) strikeRequests.add(r);
      if(overline!=null) overlineRequests.add(r);
    }
    
  }
  
  
  public void endLine() {
    closeUnderline();
    closeStrike();
    closeOverline();
  }
}
