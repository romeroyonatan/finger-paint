/*
 * GNU GENERAL PUBLIC LICENSE
 *
 * Android Paint is a Drawing Application for Android.
 * Copyright (C) 2014 Steve Jarvis
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sajarvis.paint;

import android.graphics.Paint;
import android.graphics.Path;

public class PathPaint {
	private Paint paint;
	private Path path;

	public PathPaint(Path pth, Paint pnt){
		paint = pnt;
		path = pth;
	}

	//Get the path
	public Path getPath(){
		return path;
	}

	//Get paint
	public Paint getPaint(){
		return paint;
	}
}
