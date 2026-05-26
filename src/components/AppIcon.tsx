import React from 'react';
import { View, StyleProp, ViewStyle } from 'react-native';
import { Icon } from 'react-native-paper';

interface AppIconProps {
  name: string;
  size: number;
  color?: string;
  style?: StyleProp<ViewStyle>;
}

export function AppIcon({ name, size, color, style }: AppIconProps) {
  if (style) {
    return (
      <View style={style}>
        <Icon source={name} size={size} color={color} />
      </View>
    );
  }
  return <Icon source={name} size={size} color={color} />;
}
