package com.devsurf.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Consistent 24-unit outline icons for the browser menu. */
final class MenuIcon extends View {
    private final String icon;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    MenuIcon(Context context, String icon, int color) {
        super(context); this.icon = icon; paint.setColor(color); paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.6f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    private void line(Canvas c, float... points) {
        Path path = new Path(); path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]); c.drawPath(path, paint);
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c); c.save(); c.scale(getWidth() / 24f, getHeight() / 24f);
        switch (icon) {
            case "file": line(c,5,2,14,2,20,8,20,22,5,22,5,2); line(c,14,2,14,8,20,8); line(c,9,13,16,13); line(c,9,17,16,17); break;
            case "sound": line(c,3,9,7,9,12,4,12,20,7,15,3,15,3,9); c.drawArc(10,7,20,17,-60,120,false,paint); c.drawArc(8,3,24,21,-60,120,false,paint); break;
            case "shield": line(c,12,2,21,6,20,14,17,19,12,22,7,19,4,14,3,6,12,2); line(c,8,12,11,15,16,9); break;
            case "appearance": c.drawCircle(12,12,9,paint); c.save(); c.clipRect(12,0,24,24); paint.setStyle(Paint.Style.FILL); c.drawCircle(12,12,9,paint); paint.setStyle(Paint.Style.STROKE); c.restore(); break;
            case "delete": line(c,4,6,20,6); line(c,9,6,9,3,15,3,15,6); line(c,6,6,7,21,17,21,18,6); line(c,10,10,10,17); line(c,14,10,14,17); break;
            case "info": c.drawCircle(12,12,9,paint); line(c,12,11,12,17); c.drawPoint(12,7,paint); break;
            case "star": line(c, 12,3, 14.8f,8.8f, 21.2f,9.7f, 16.6f,14.2f, 17.7f,20.6f, 12,17.6f, 6.3f,20.6f, 7.4f,14.2f, 2.8f,9.7f, 9.2f,8.8f, 12,3); break;
            case "share": c.drawCircle(18,5,2.5f,paint); c.drawCircle(6,12,2.5f,paint); c.drawCircle(18,19,2.5f,paint); line(c,8.2f,10.7f,15.8f,6.3f); line(c,8.2f,13.3f,15.8f,17.7f); break;
            case "find": c.drawCircle(10.5f,10.5f,6.5f,paint); line(c,15.2f,15.2f,21,21); break;
            case "desktop": c.drawRoundRect(3,4,21,17,2,2,paint); line(c,12,17,12,21); line(c,8,21,16,21); break;
            case "history": c.drawArc(4,4,21,21,215,310,false,paint); line(c,3,4,3,9,8,9); line(c,12,7,12,12,16,14); break;
            case "download": line(c,12,3,12,15); line(c,7,10,12,15,17,10); line(c,4,16,4,21,20,21,20,16); break;
            case "chat": c.drawRoundRect(3,3,21,18,3,3,paint); line(c,6,18,6,22,11,18); line(c,7,8,17,8); line(c,7,12,14,12); break;
            case "settings":
                c.drawCircle(12,12,3,paint);
                for (int i = 0; i < 8; i++) { c.save(); c.rotate(i * 45,12,12); line(c,10,5,10,2.5f,14,2.5f,14,5); c.restore(); }
                c.drawCircle(12,12,7.3f,paint); break;
        }
        c.restore();
    }
}
